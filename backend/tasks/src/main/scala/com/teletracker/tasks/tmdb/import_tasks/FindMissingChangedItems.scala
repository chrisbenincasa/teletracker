package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.common.util.{AsyncStream, ClosedDateRange}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.tmdb.export_tasks.ChangesDumpFileRow
import io.circe.Encoder
import io.circe.parser._
import io.circe._
import io.circe.generic.semiauto.deriveCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.time.LocalDate
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

case class FindMissingChangedItemsArgs(
  start: LocalDate,
  end: LocalDate,
  itemType: ItemType)

class FindMissingChangedItems @Inject()(
  teletrackerConfig: TeletrackerConfig,
  s3Client: S3Client,
  itemLookup: ItemLookup,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override type TypedArgs = FindMissingChangedItemsArgs

  implicit protected val tDecoder: Decoder[ChangesDumpFileRow] =
    deriveCodec

  implicit override protected def typedArgsEncoder
    : Encoder[FindMissingChangedItemsArgs] =
    io.circe.generic.semiauto.deriveEncoder

  override def preparseArgs(args: Args): FindMissingChangedItemsArgs =
    FindMissingChangedItemsArgs(
      start = args.valueOrThrow[LocalDate]("start"),
      end = args.valueOrThrow[LocalDate]("end"),
      itemType = args.valueOrThrow[ItemType]("itemType")
    )

  override protected def runInternal(args: Args): Unit = {
    val parsedArgs = preparseArgs(args)

    val missingIds = AsyncStream
      .fromSeq(ClosedDateRange(parsedArgs.start, parsedArgs.end).days.reverse)
      .flatMapSeq(date => {
        logger.info(s"Pulling dump from ${date}")

        val objectAsString = s3Client
          .getObjectAsBytes(
            GetObjectRequest
              .builder()
              .bucket(teletrackerConfig.data.s3_bucket)
              .key(
                s"scrape-results/tmdb/${date}/${date}_${parsedArgs.itemType}-changes.json"
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
                id =>
                  (ExternalSource.TheMovieDb, id.toString, parsedArgs.itemType)
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
          parsedArgs.itemType match {
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
