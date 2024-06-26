package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.{AsyncStream, ClosedDateRange}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.tmdb.export_tasks.ChangesDumpFileRow
import io.circe.parser._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.time.LocalDate
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

@JsonCodec
@GenArgParser
case class FindMissingChangedItemsArgs(
  start: LocalDate,
  end: LocalDate,
  itemType: ItemType)

object FindMissingChangedItemsArgs

class FindMissingChangedItems @Inject()(
  teletrackerConfig: TeletrackerConfig,
  s3Client: S3Client,
  itemLookup: ItemLookup,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[FindMissingChangedItemsArgs] {

  override protected def runInternal(): Unit = {
    val missingIds = AsyncStream
      .fromSeq(ClosedDateRange(args.start, args.end).days.reverse)
      .flatMapSeq(date => {
        logger.info(s"Pulling dump from ${date}")

        val objectAsString = s3Client
          .getObjectAsBytes(
            GetObjectRequest
              .builder()
              .bucket(teletrackerConfig.data.s3_bucket)
              .key(
                s"scrape-results/tmdb/${date}/${date}_${args.itemType}-changes.json"
              )
              .build()
          )
          .asUtf8String()

        objectAsString
          .split("\n")
          .flatMap(
            line =>
              decode[ChangesDumpFileRow](line) match {
                case Left(value) =>
                  logger.error("Could not parse", value)
                  None
                case Right(value) => Some(value.id)
              }
          )
      })
      .distinct
      .grouped(50)
      .mapF(batch => {
        itemLookup
          .lookupItemsByExternalIds(
            batch
              .map(
                id => (ExternalSource.TheMovieDb, id.toString, args.itemType)
              )
              .toList
          )
          .map(results => {
            val keys = results.keySet.map(_._1.id.toInt)
            batch.toSet -- keys
          })
      })
      .foldLeft(Set.empty[Int])(_ ++ _)
      .await()

    val scheduledService = Executors.newSingleThreadScheduledExecutor()

    logger.info(s"Found ${missingIds.size} potentially missing ids")

    val actuallyMissingIds = AsyncStream
      .fromSeq(missingIds.toSeq)
      .delayedMapF(250.millis, scheduledService)(
        item => {
          args.itemType match {
            case ItemType.Movie =>
              itemExpander.expandMovie(item).map(_ => Some(item)).recover {
                case NonFatal(e) => None
              }
            case ItemType.Show =>
              itemExpander.expandTvShow(item).map(_ => Some(item)).recover {
                case NonFatal(e) => None
              }
            case ItemType.Person =>
              itemExpander.expandPerson(item).map(_ => Some(item)).recover {
                case NonFatal(e) => None
              }
          }
        }
      )
      .flatMap(AsyncStream.fromOption)
      .toSeq()
      .await()

    if (actuallyMissingIds.nonEmpty) {
      logger.info(
        s"Missing ids = ${actuallyMissingIds.mkString("(", ", ", ")")}"
      )
    } else {
      logger.info("Found no missing ids")
    }
  }
}
