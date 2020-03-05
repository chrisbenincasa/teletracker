package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.tmdb.{
  UpdateMoviePopularities,
  UpdatePopularities,
  UpdatePopularitiesJobArgs,
  UpdateTvShowPopularities
}
import com.teletracker.tasks.util.{SourceRetriever, TaskMessageHelper}
import io.circe.Encoder
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.{SqsAsyncClient, SqsClient}
import java.net.URI
import java.time.LocalDate
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

abstract class LocatePopularityDeltas[T <: UpdatePopularities[_]: ClassTag](
  itemType: ThingType,
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit enc: Encoder.AsObject[T#TypedArgs],
  executionContext: ExecutionContext)
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

    s"scrape-results/tmdb/$today/${typeString}_ids_sorted-$today.json.gz"
  }

  override protected def makeTaskMessages(
    snapshotBeforeLocation: URI,
    snapshotAfterLocation: URI,
    args: Args
  ): List[TeletrackerTaskQueueMessage] = {
    val mod = args.value[Int]("mod")

    mod match {
      case Some(value) =>
        (0 until value).toList.map(band => {
          // TODO: Pass as args, or use configuration
          TaskMessageHelper.forTask[T](
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
        TaskMessageHelper.forTask[T](
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
  publisher: SqsAsyncClient,
  s3Client: S3Client,
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
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
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends LocatePopularityDeltas[UpdateTvShowPopularities](
      ThingType.Show,
      publisher,
      s3Client,
      sourceRetriever,
      teletrackerConfig
    )

//class LocatePersonPopularityDelta @Inject()(
//  publisher: SqsAsyncClient,
//  s3Client: S3Client,
//  sourceRetriever: SourceRetriever,
//  teletrackerConfig: TeletrackerConfig)
//    extends LocatePopularityDeltas[](
//      ThingType.Person,
//      publisher,
//      s3Client,
//      sourceRetriever,
//      teletrackerConfig
//    )
