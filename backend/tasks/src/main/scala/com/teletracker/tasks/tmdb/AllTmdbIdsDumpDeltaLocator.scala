package com.teletracker.tasks.tmdb

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.{
  DeltaLocatorJob,
  DeltaLocatorJobArgs,
  DeltaLocatorJobArgsLike
}
import com.teletracker.common.util.Functions._
import com.teletracker.tasks.tmdb.import_tasks.{
  DeleteItemsFromAllIdsDumps,
  DeleteItemsFromAllIdsDumpsArgs
}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import scala.concurrent.ExecutionContext

@JsonCodec
@GenArgParser
case class AllTmdbIdsDumpDeltaLocatorArgs(
  override val maxDaysBack: Int = 3,
  override val local: Boolean = false,
  override val seedDumpDate: Option[LocalDate],
  itemType: ItemType)
    extends DeltaLocatorJobArgsLike

class AllTmdbIdsDumpDeltaLocator @Inject()(
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends DeltaLocatorJob[AllTmdbIdsDumpDeltaLocatorArgs](
      s3Client,
      teletrackerConfig
    ) {
  override protected def postParseArgs(
    halfParsed: DeltaLocatorJobArgs
  ): AllTmdbIdsDumpDeltaLocatorArgs = {
    AllTmdbIdsDumpDeltaLocatorArgs(
      maxDaysBack = halfParsed.maxDaysBack,
      local = halfParsed.local,
      seedDumpDate = halfParsed.seedDumpDate,
      itemType = rawArgs.valueOrThrow[ItemType]("itemType")
    )
  }

  override protected def getKey(today: LocalDate): String = {
    val typeString = args.itemType match {
      case ItemType.Movie  => "movie"
      case ItemType.Show   => "tv_series"
      case ItemType.Person => "person"
    }

    s"scrape-results/tmdb/$today/${typeString}_ids_sorted-$today.json.gz"
  }

  override protected def makeTaskMessages(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI
  ): List[TeletrackerTaskQueueMessage] = {
    val mod = rawArgs.value[Int]("mod")
    val popularitiesJobArgs = UpdatePopularitiesJobArgs(
      snapshotBeforeLocation,
      snapshotAfterLocation,
      offset = 0,
      limit = -1,
      dryRun = false,
      changeThreshold = 1.0f,
      mod = None,
      band = None
    )

    val popularityMessages = mod match {
      case Some(value) =>
        (0 until value).toList.map(band => {
          // TODO: Pass as args, or use configuration
          popularityTaskMessages(
            popularitiesJobArgs.copy(mod = Some(value), band = Some(band))
          )
        })

      case None =>
        // TODO: Pass as args, or use configuration
        popularityTaskMessages(popularitiesJobArgs) :: Nil
    }

    val deleteJob = TeletrackerTask.taskMessage[DeleteItemsFromAllIdsDumps](
      DeleteItemsFromAllIdsDumpsArgs(
        snapshotBeforeLocation = snapshotBeforeLocation,
        snapshotAfterLocation = snapshotAfterLocation,
        dryRun = rawArgs.dryRun,
        sleepBetweenWriteMs = None,
        itemType = args.itemType
      )
    )

    popularityMessages :+ deleteJob
  }

  private def popularityTaskMessages(
    popularityArgs: UpdatePopularitiesJobArgs
  ): TeletrackerTaskQueueMessage = {
    args.itemType match {
      case ItemType.Movie =>
        TeletrackerTask.taskMessage[UpdateMoviePopularities](popularityArgs)
      case ItemType.Show =>
        TeletrackerTask.taskMessage[UpdateTvShowPopularities](popularityArgs)
      case ItemType.Person =>
        TeletrackerTask.taskMessage[UpdatePeoplePopularities](popularityArgs)
    }
  }
}
