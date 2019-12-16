package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.tmdb.{
  UpdateMoviePopularities,
  UpdatePopularities,
  UpdatePopularitiesJobArgs
}
import com.teletracker.tasks.util.{SourceRetriever, TaskMessageHelper}
import io.circe.Encoder
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import java.net.URI
import java.time.LocalDate
import scala.reflect.ClassTag

abstract class LocatePopularityDeltas[T <: UpdatePopularities[_]: ClassTag](
  itemType: ThingType,
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#TypedArgs])
    extends DeltaLocatorJob(
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    ) {

  override protected def getKey(today: LocalDate): String = {
    val typeString = itemType match {
      case ThingType.Movie  => "movie"
      case ThingType.Show   => "tv_series"
      case ThingType.Person => "person"
    }

    s"scrape-results/tmdb/$today/${typeString}_ids_sorted-2019-12-13.json.gz"
  }

  override protected def makeTaskMessage(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI,
    args: Args
  ): TeletrackerTaskQueueMessage = {
    // TODO: Pass as args, or use configuration
    TaskMessageHelper.forTask[T](
      UpdatePopularitiesJobArgs(
        snapshotBeforeLocation,
        snapshotAfterLocation,
        offset = 0,
        limit = -1,
        dryRun = false,
        changeThreshold = 1.0f
      )
    )
  }
}

class LocateMoviePopularityDelta @Inject()(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends LocatePopularityDeltas[UpdateMoviePopularities](
      ThingType.Movie,
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    )

class LocateShowPopularityDelta @Inject()(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends LocatePopularityDeltas[UpdateMoviePopularities](
      ThingType.Show,
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    )

class LocatePersonPopularityDelta @Inject()(
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig)
    extends LocatePopularityDeltas[UpdateMoviePopularities](
      ThingType.Person,
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    )
