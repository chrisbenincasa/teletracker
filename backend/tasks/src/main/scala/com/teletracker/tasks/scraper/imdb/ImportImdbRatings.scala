package com.teletracker.tasks.scraper.imdb

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemUpdater
import com.teletracker.common.elasticsearch.ItemUpdater.{
  FailureResult,
  UpdateItemResult
}
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItem,
  EsItemRating
}
import com.teletracker.common.pubsub.EsIngestItemDenormArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures.richFuture
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.{AsyncStream, Ratings, S3Uri}
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.model.BaseTaskArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{S3Selector, SourceRetriever}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.net.URI
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.UUID
import scala.compat.java8.StreamConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

@GenArgParser
@JsonCodec
case class ImportImdbRatingsArgs(
  imdbRatingsInput: URI,
  queryLimit: Int = -1, // For testing only
  limit: Int = -1,
  itemType: ItemType = ItemType.Movie,
  ratingWeight: Int = 5000,
  popularityGte: Option[Double],
  popularityLt: Option[Double],
  async: Boolean = true,
  countOnly: Boolean = false,
  override val dryRun: Boolean = true,
  override val sleepBetweenWriteMs: Option[Long])
    extends BaseTaskArgs

object ImportImdbRatingsArgs

class ImportImdbRatings @Inject()(
  teletrackerConfig: TeletrackerConfig,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[ImportImdbRatingsArgs] {

  private def generateQuery: String =
    s"""
      |select s."type", s.external_ids, s.id, s.popularity, s.ratings
      | from s3object[*]._source s 
      | where s."type" = '${args.itemType}'
      | ${if (args.popularityGte.isDefined)
         s"and s.popularity >= ${args.popularityGte.get}"
       else ""}
      | ${if (args.popularityLt.isDefined)
         s"and s.popularity < ${args.popularityLt.get}"
       else ""}
      |""".stripMargin

  override protected def runInternal(): Unit = {
    val legacyS3Client = AmazonS3ClientBuilder.defaultClient()

    logger.info("Reading IMDB mappings")

    val imdbIdToRatings = sourceRetriever
      .getSource(args.imdbRatingsInput)
      .getLines()
      .drop(1)
      .map(line => {
        val parts = line.split('\t')
        parts.head -> (parts(1).toDouble, parts(2).toInt)
      })
      .toMap

    val allRatings = imdbIdToRatings.values
    val imdbAverage = allRatings.map(_._1).sum / allRatings.size

    val mostRecentPrefix = s3Client
      .listObjectsV2Paginator(
        ListObjectsV2Request
          .builder()
          .bucket(teletrackerConfig.data.s3_bucket)
          .prefix("elasticsearch/items/")
          .delimiter("/")
          .build()
      )
      .commonPrefixes()
      .stream()
      .toScala
      .last
      .prefix()

    val outputDir = Files.createTempDirectory("import_imdb_ratings")

    val selector = new S3Selector(legacyS3Client)

    val now = Instant.now()

    logger.info(s"Selecting items with query:\n${generateQuery}")

    val total = AsyncStream
      .fromStream(
        sourceRetriever
          .getUriStream(
            URI
              .create(
                s"s3://${teletrackerConfig.data.s3_bucket}/$mostRecentPrefix"
              )
          )
      )
      .safeTake(args.queryLimit)
      .collect {
        case uri @ S3Uri(_, _) => uri
      }
      .mapConcurrent(selector.maxOutstanding) {
        case S3Uri(bucket, key) =>
          selector.select(bucket, key, generateQuery, outputDir)

        case _ => throw new IllegalStateException("Impossible.")
      }
      .flatMapSeq(file => {
        val source = Source.fromFile(file)
        try {
          new IngestJobParser()
            .stream[ImdbRatingsEsItem](source.getLines())
            .flatMap {
              case Left(value) =>
                logger.error(s"Could not parse line", value)
                None
              case Right(value) =>
                Some(value)
            }
            .filter(
              item =>
                item.external_ids
                  .exists(_.provider == ExternalSource.Imdb.toString)
            )
            .flatMap(item => {
              item.externalIdsGrouped
                .get(ExternalSource.Imdb)
                .flatMap(imdbIdToRatings.get)
                .flatMap {
                  case (rating, votes) =>
                    val imdbWeighted = Ratings.weightedAverage(
                      rating,
                      votes,
                      imdbAverage,
                      args.ratingWeight
                    )

                    val newRating = EsItemRating(
                      source = ExternalSource.Imdb,
                      voteAverage = rating,
                      voteCount = Some(votes),
                      weightedAverage = Some(imdbWeighted),
                      weightedLastGenerated = Some(now)
                    )

                    item.ratingsGrouped.get(ExternalSource.Imdb) match {
                      case Some(existing) =>
                        if (existing.vote_average == rating && existing.vote_count
                              .contains(votes) && existing.weighted_average
                              .contains(imdbWeighted)) {
                          None
                        } else {
                          Some(item -> newRating)

                        }

                      case None => Some(item -> newRating)
                    }
                }
            })
            .toList
        } finally {
          source.close()
        }
      })
      .safeTake(args.limit)
      .foldLeftF(0L) {
        case (acc, (item, rating)) =>
          if (args.countOnly) {
            Future.successful(acc + 1)
          } else {
            if (!args.dryRun) {
              logger.debug(
                s"Updating ${item.id} (async=${args.async}) with new rating: ${rating}"
              )

              itemUpdater
                .updateWithScript(
                  id = item.id,
                  itemType = item.`type`,
                  script = ItemUpdater.UpsertRatingScript(rating),
                  async = args.async,
                  denormArgs = Some(
                    EsIngestItemDenormArgs(
                      needsDenorm = true,
                      cast = false,
                      crew = false
                    )
                  )
                )
                .map {
                  case UpdateItemResult(FailureResult(_, reason)) =>
                    logger.error(
                      s"Could not queue update for item id ${item.id}. Reason: ${reason}"
                    )

                    acc
                  case _ => acc + 1
                }
            } else {
              Future.successful {
                val json = itemUpdater.getScriptUpdateJson(
                  item.id,
                  ItemUpdater.UpsertRatingScript(rating)
                )

                logger
                  .info(s"Would've updated id ${item.id} with:\n${json}")

                acc + 1
              }
            }
          }
      }
      .await()

    if (args.countOnly) {
      logger.info(s"${total} items matched and would potentially be updated")
    } else if (args.dryRun) {
      logger.info(s"Would've updated a total of ${total} items")
    } else {
      logger.info(s"Updated ${total} total items")
    }
  }
}

@JsonCodec
case class ImdbRatingsEsItem(
  id: UUID,
  `type`: ItemType,
  external_ids: List[EsExternalId],
  popularity: Double,
  ratings: Option[List[EsItemRating]]) {

  def ratingsGrouped: Map[ExternalSource, EsItemRating] =
    EsItem.ratingsGrouped(ratings.getOrElse(Nil))

  def externalIdsGrouped: Map[ExternalSource, String] =
    EsItem.externalIdsGrouped(external_ids)
}
