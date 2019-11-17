package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.common.pubsub.{JobTags, TeletrackerTaskQueueMessage}
import com.teletracker.tasks.SchedulesFollowupTasks
import com.teletracker.tasks.annotations.TaskTags
import com.teletracker.tasks.tmdb.import_tasks.{
  ImportMoviesFromDump,
  ImportPeopleFromDump,
  ImportTmdbDumpTaskArgs,
  ImportTvShowsFromDump
}
import com.teletracker.tasks.util.ArgJsonInstances._
import com.teletracker.tasks.util.TaskMessageHelper
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient
import scala.concurrent.{ExecutionContext, Future}

@TaskTags(tags = Array(JobTags.RequiresTmdbApi))
abstract class ChangesDumpTask(
  thingType: ThingType,
  s3: S3Client,
  itemExpander: ItemExpander,
  protected val publisher: SqsClient
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[ChangesDumpFileRow](s3)
    with SchedulesFollowupTasks {
  implicit override protected val tDecoder: Decoder[ChangesDumpFileRow] =
    deriveCodec

  override protected def getRawJson(currentId: Int): Future[String] = {
    val extraFields = thingType match {
      case ThingType.Movie =>
        List("recommendations", "similar", "videos")
      case ThingType.Show =>
        List("recommendations", "similar", "videos")
      case ThingType.Person =>
        List("tagged_images")
    }

    itemExpander.expandRaw(thingType, currentId, extraFields).map(_.noSpaces)
  }

  override protected def baseFileName: String = s"$thingType-delta"
}

class MovieChangesDumpTask @Inject()(
  s3: S3Client,
  itemExpander: ItemExpander,
  publisher: SqsClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Movie, s3, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportMoviesFromDump](
      ImportTmdbDumpTaskArgs(
        input = s3Uri,
        dryRun = false
      )
    ) :: Nil
  }
}

@TaskTags(tags = Array(JobTags.RequiresTmdbApi))
class TvChangesDumpTask @Inject()(
  s3: S3Client,
  itemExpander: ItemExpander,
  publisher: SqsClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Show, s3, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportTvShowsFromDump](
      ImportTmdbDumpTaskArgs(
        input = s3Uri,
        dryRun = false
      )
    ) :: Nil
  }
}

class PersonChangesDumpTask @Inject()(
  s3: S3Client,
  itemExpander: ItemExpander,
  publisher: SqsClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Person, s3, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportPeopleFromDump](
      ImportTmdbDumpTaskArgs(
        input = s3Uri,
        dryRun = false
      )
    ) :: Nil
  }
}

case class ChangesDumpFileRow(
  id: Int,
  adult: Option[Boolean])
    extends TmdbDumpFileRow {
  override def popularity: Double = 0.0
}
