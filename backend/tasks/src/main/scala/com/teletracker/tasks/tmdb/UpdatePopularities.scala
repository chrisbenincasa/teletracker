package com.teletracker.tasks.tmdb

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.pubsub.{
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestUpdate
}
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Functions._
import com.teletracker.tasks.TeletrackerTask
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  TmdbDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import javax.inject.Inject
import org.elasticsearch.common.io.stream.BytesStreamOutput
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class UpdatePopularitiesDependencies @Inject()(
  val sourceRetriever: SourceRetriever,
  val ingestJobParser: IngestJobParser,
  val itemLookup: ItemLookup,
  val teletrackerConfig: TeletrackerConfig,
  val sqsAsyncClient: SqsAsyncClient)

class UpdateMoviePopularities @Inject()(
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdatePopularities[MovieDumpFileRow](
      ThingType.Movie,
      deps
    )

class UpdateTvShowPopularities @Inject()(
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends UpdatePopularities[TvShowDumpFileRow](
      ThingType.Show,
      deps
    )

@JsonCodec
case class UpdatePopularitiesJobArgs(
  snapshotBefore: URI,
  snapshotAfter: URI,
  offset: Int,
  limit: Int,
  dryRun: Boolean,
  changeThreshold: Float,
  verbose: Boolean = false,
  mod: Option[Int],
  band: Option[Int])

abstract class UpdatePopularities[T <: TmdbDumpFileRow: Decoder](
  itemType: ThingType,
  deps: UpdatePopularitiesDependencies
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _

  override type TypedArgs = UpdatePopularitiesJobArgs

  implicit override protected lazy val typedArgsEncoder
    : Encoder[UpdatePopularitiesJobArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): UpdatePopularitiesJobArgs = {
    UpdatePopularitiesJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.valueOrThrow[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      changeThreshold = args.valueOrDefault("changeThreshold", 1.0f),
      verbose = args.valueOrDefault("verbose", false),
      mod = args.value[Int]("mod"),
      band = args.value[Int]("band")
    )
  }

  override def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)

    if (parsedArgs.band.isDefined && parsedArgs.mod.isEmpty || parsedArgs.band.isEmpty && parsedArgs.mod.isDefined) {
      throw new IllegalArgumentException("Both mod and band must be defined")
    }

    val hasModAndBand = parsedArgs.band.isDefined && parsedArgs.mod.isDefined

    // Input must be SORTED BY POPULARITY DESC
    val beforeSource = deps.sourceRetriever.getSource(parsedArgs.snapshotBefore)
    val afterSource = deps.sourceRetriever.getSource(parsedArgs.snapshotAfter)

    val beforePopularitiesById = deps.ingestJobParser
      .stream[T](beforeSource.getLines())
      .applyIf(hasModAndBand)(
        _.valueFilterMod({
          case Right(value) => value.id
        }, parsedArgs.mod.get, parsedArgs.band.get)
      )
      .flatMap {
        case Left(value) =>
          logger.error("Could not parse line", value)
          None

        case Right(value) => Some(value.id -> value.popularity)
      }
      .toMap

    logger.info("Finished parsing before delta")

    val afterPopularitiesById = deps.ingestJobParser
      .stream[T](afterSource.getLines())
      .applyIf(hasModAndBand)(
        _.valueFilterMod({
          case Right(value) => value.id
        }, parsedArgs.mod.get, parsedArgs.band.get)
      )
      .flatMap {
        case Left(value) =>
          logger.error("Could not parse line", value)
          None

        case Right(value) => Some(value.id -> value.popularity)
      }
      .toMap

    logger.info("Finished parsing after delta")

    val afterSourceForUpdate =
      deps.sourceRetriever
        .getSource(parsedArgs.snapshotAfter, consultCache = true)

    val processed = new AtomicInteger()

    deps.ingestJobParser
      .asyncStream[T](afterSourceForUpdate.getLines())
      .drop(parsedArgs.offset)
      .safeTake(parsedArgs.limit)
      .flatMapOption(_.toOption)
      .filter(line => afterPopularitiesById.isDefinedAt(line.id))
      .flatMapOption(item => {
        if (!beforePopularitiesById.isDefinedAt(item.id)) {
          Some(item.id -> afterPopularitiesById(item.id))
        } else {
          val afterPopularity = afterPopularitiesById(item.id)
          val beforePopularity = beforePopularitiesById(item.id)

          val delta = Math.abs(afterPopularity - beforePopularity)

          val meetsThreshold = delta >= parsedArgs.changeThreshold

          if (parsedArgs.verbose && meetsThreshold) {
            logger.info(
              s"Id = ${item.id} meets threshold with difference = ${afterPopularity - beforePopularity}. " +
                s"New popularity: ${afterPopularity}"
            )
          }

          if (meetsThreshold) {
            Some(item.id -> afterPopularitiesById(item.id))
          } else {
            None
          }
        }
      })
      .grouped(50)
      .foreachF(batch => {
        processed.addAndGet(batch.size)

        val output = new BytesStreamOutput()

        if (!parsedArgs.dryRun) {
          val queue = new SqsQueue[EsIngestMessage](
            deps.sqsAsyncClient,
            deps.teletrackerConfig.async.esIngestQueue.url
          )

          AsyncStream
            .fromSeq(batch)
            .grouped(10)
            .mapF(innerBatch => {
              logger.debug(s"Looking up ${innerBatch.size} items")
              deps.itemLookup
                .lookupItemsByExternalIds(
                  innerBatch
                    .map(_._1)
                    .map(
                      id => (ExternalSource.TheMovieDb, id.toString, itemType)
                    )
                    .toList
                )
                .map(results => {
                  results.map {
                    case ((_, id), item) => id.toInt -> item.id
                  }
                })
                .flatMap(itemIdById => {
                  logger.debug(s"Found ${itemIdById.size} items")
                  val messages = innerBatch
                    .flatMap {
                      case (tmdbId, popularity) =>
                        itemIdById.get(tmdbId).map(_ -> popularity)
                    }
                    .map {
                      case (id, popularity) =>
                        EsIngestMessage(
                          EsIngestMessageOperation.Update,
                          update = Some(
                            EsIngestUpdate(
                              index =
                                teletrackerConfig.elasticsearch.items_index_name,
                              id.toString,
                              None,
                              Some(Map("popularity" -> popularity).asJson)
                            )
                          )
                        )
                    }

                  queue.batchQueue(messages.toList)
                })
            })
            .force
            .map(_ => {
              output.bytes().writeTo(Console.out)
            })
        } else {
          Future.unit
        }
      })
      .await()

    if (parsedArgs.dryRun) {
      logger.info(s"Would've processed ${processed.get()} total popularities")
    } else {
      logger.info(s"Processed ${processed.get()} total popularities")
    }
  }
}