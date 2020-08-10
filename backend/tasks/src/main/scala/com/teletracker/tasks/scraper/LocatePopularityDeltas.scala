package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.{TaskMessageHelper, TeletrackerTask}
import com.teletracker.tasks.tmdb.{
  UpdateMoviePopularities,
  UpdatePeoplePopularities,
  UpdatePopularities,
  UpdatePopularitiesJobArgs,
  UpdateTvShowPopularities
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Encoder
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

abstract class LocatePopularityDeltas[T <: UpdatePopularities[_]: ClassTag](
  itemType: ItemType,
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#ArgsType],
  executionContext: ExecutionContext)
    extends DeltaLocatorJob[DeltaLocatorJobArgs](
      s3Client,
      teletrackerConfig
    ) {
  override protected def getKey(today: LocalDate): String = {
    val typeString = itemType match {
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

    mod match {
      case Some(value) =>
        (0 until value).toList.map(band => {
          // TODO: Pass as args, or use configuration
          TeletrackerTask.taskMessage[T](
            UpdatePopularitiesJobArgs(
              snapshotBeforeLocation,
              snapshotAfterLocation,
              offset = 0,
              limit = -1,
              dryRun = false,
              changeThreshold = 1.0f,
              mod = Some(value),
              band = Some(band)
            )
          )
        })
      case None =>
        // TODO: Pass as args, or use configuration
        TeletrackerTask.taskMessage[T](
          UpdatePopularitiesJobArgs(
            snapshotBeforeLocation,
            snapshotAfterLocation,
            offset = 0,
            limit = -1,
            dryRun = false,
            changeThreshold = 1.0f,
            mod = None,
            band = None
          )
        ) :: Nil
    }

  }
}

class LocateMoviePopularityDelta @Inject()(
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends LocatePopularityDeltas[UpdateMoviePopularities](
      ItemType.Movie,
      s3Client,
      teletrackerConfig
    )

class LocateShowPopularityDelta @Inject()(
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends LocatePopularityDeltas[UpdateTvShowPopularities](
      ItemType.Show,
      s3Client,
      teletrackerConfig
    )

class LocatePersonPopularityDelta @Inject()(
  s3Client: S3Client,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends LocatePopularityDeltas[UpdatePeoplePopularities](
      ItemType.Person,
      s3Client,
      teletrackerConfig
    )
