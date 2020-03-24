package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsExternalId
}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.ClosedDateRange
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.ChangesDumpFileRow
import com.teletracker.tasks.util.SourceWriter
import io.circe._
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.common.io.stream.OutputStreamStreamOutput
import org.elasticsearch.common.xcontent.json.JsonXContent
import org.elasticsearch.common.xcontent.{
  ToXContent,
  XContent,
  XContentBuilder,
  XContentHelper,
  XContentType
}
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.UpdateByQueryRequest
import org.elasticsearch.script.{Script, ScriptType}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  NoSuchKeyException
}
import java.io.{File, FileOutputStream, PrintStream}
import java.net.URI
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.control.NonFatal

class DumpTmdbChanges @Inject()(
  s3: S3Client,
  sourceWriter: SourceWriter
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  implicit protected val tDecoder: Codec[ChangesDumpFileRow] =
    deriveCodec

  final private val useOldBucketCutoff = LocalDate.of(2020, 2, 10)
  final private lazy val usWest1S3 =
    S3Client.builder().region(Region.US_WEST_1).build()

  override protected def runInternal(args: Args): Unit = {
    val after = args.valueOrThrow[LocalDate]("after")
    val before = args.valueOrDefault[LocalDate]("before", LocalDate.now())
    val itemType = args.valueOrThrow[ItemType]("type")

    val today = LocalDate.now()
    val range = ClosedDateRange(after, before)

    val allChanges = range.days.reverse.toStream
      .map(date => {
        fetchChangesJson(itemType, date)
      })
      .foldLeft(List.empty[ChangesDumpFileRow]) {
        case (acc, next) =>
          val nextIds = next.map(_.id).toSet
          acc.filterNot(row => nextIds.contains(row.id)) ++ next
      }

    val destFile = System.getProperty("user.dir") + s"/out/${after}_${today}-$itemType-changes.json"

    val outFile = new File(destFile)
    val writer = new PrintStream(new FileOutputStream(outFile))

    allChanges.foreach(row => {
      writer.println(row.asJson.noSpaces)
    })

    writer.flush()
    writer.close()
  }

  private def fetchChangesJson(
    thingType: ItemType,
    date: LocalDate
  ) = {
    val (clientToUse, bucket) =
      if (date
            .isEqual(useOldBucketCutoff) || date.isBefore(useOldBucketCutoff)) {
        usWest1S3 -> "teletracker-data"
      } else {
        s3 -> "teletracker-data-us-west-2"
      }

    val lines = try {
      clientToUse
        .getObjectAsBytes(
          GetObjectRequest
            .builder()
            .bucket(bucket)
            .key(s"scrape-results/tmdb/$date/${date}_${thingType}-changes.json")
            .build()
        )
        .asUtf8String()
        .split("\n")
    } catch {
      case e: NoSuchKeyException =>
        logger.error(
          s"Could not find key: s3://$bucket/${date}_${thingType}-changes.json"
        )
        throw e
    }

    new IngestJobParser()
      .stream[ChangesDumpFileRow](lines.iterator)
      .collect {
        case Right(value) => value
      }
      .toList
      .groupBy(_.id)
      .mapValues(_.head)
      .values
  }
}

class UpdateAdultBit @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  implicit protected val tDecoder: Codec[ChangesDumpFileRow] =
    deriveCodec

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private val totalUpdated = new AtomicLong(0)

  final val UpdateAdultBitScriptSource =
    """
      |if (ctx._source.adult == null || ctx._source.adult != params.adult) {
      |  ctx._source.adult = params.adult
      |}  
      |""".stripMargin

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val itemType = args.valueOrThrow[ItemType]("type")
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val dryRun = args.valueOrDefault("dryRun", true)
    val adultType = args.valueOrDefault("adultType", true)

    val source = Source.fromURI(input)
    new IngestJobParser()
      .asyncStream[ChangesDumpFileRow](source.getLines())
      .collect {
        case Right(value) if value.adult.contains(adultType) => value
      }
      .drop(offset)
      .safeTake(limit)
      .grouped(25)
      .delayedMapF(1 second, scheduler)(
        batch => {
          val baseQuery = QueryBuilders
            .boolQuery()
            .must(QueryBuilders.termQuery("type", itemType.toString))
            .must(
              QueryBuilders
                .boolQuery()
                .should(QueryBuilders.termQuery("adult", !adultType))
                .should(
                  QueryBuilders
                    .boolQuery()
                    .mustNot(QueryBuilders.existsQuery("adult"))
                )
                .minimumShouldMatch(1)
            )

          val shoulds = batch
            .foldLeft(baseQuery) {
              case (query, item) =>
                query
                  .should(
                    QueryBuilders
                      .termQuery(
                        "external_ids",
                        EsExternalId(
                          ExternalSource.TheMovieDb,
                          item.id.toString
                        ).toString
                      )
                  )
            }
            .minimumShouldMatch(1)

          val updateByQueryRequest = new UpdateByQueryRequest(
            teletrackerConfig.elasticsearch.items_index_name
          )

          updateByQueryRequest.setQuery(shoulds)
          updateByQueryRequest.setScript(
            new Script(
              ScriptType.INLINE,
              "painless",
              UpdateAdultBitScriptSource,
              Map[String, Object](
                "adult" -> new java.lang.Boolean(adultType)
              ).asJava
            )
          )

//          println(
//            XContentHelper
//              .toXContent(
//                shoulds,
//                XContentType.JSON,
//                ToXContent.EMPTY_PARAMS,
//                true
//              )
//              .utf8ToString()
//          )

          if (dryRun) {
            Future.successful {
              logger.info(
                s"Would've updated type = ${itemType} for ids = ${batch.map(_.id).mkString(",")}"
              )
            }
          } else {
            logger.info(
              s"Updating type = ${itemType} for ids = ${batch.map(_.id).mkString("[", ", ", "]")}"
            )

            elasticsearchExecutor
              .updateByQuery(updateByQueryRequest)
              .map(response => {
                totalUpdated.addAndGet(response.getUpdated)
                printUpdated()
              })
              .recover {
                case NonFatal(e) =>
                  logger.error(
                    s"Error while updating type = ${itemType} for ids = ${batch.map(_.id).mkString(",")}",
                    e
                  )
              }

          }
        }
      )
      .force
      .await()

    source.close()
  }

  private def printUpdated() = {
    val updated = totalUpdated.get()
    if (updated % 500 == 0) {
      logger.info(s"Updated ${updated} items so far")
    }
  }
}
