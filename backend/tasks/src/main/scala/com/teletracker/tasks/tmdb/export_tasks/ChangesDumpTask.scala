package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.common.pubsub.{TaskTag, TeletrackerTaskQueueMessage}
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.tasks.annotations.TaskTags
import com.teletracker.tasks.tmdb.import_tasks.{
  ImportMoviesFromDump,
  ImportPeopleFromDump,
  ImportTmdbDumpTaskArgs,
  ImportTvShowsFromDump
}
import com.teletracker.tasks.util.ArgJsonInstances._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.{ExecutionContext, Future}

@TaskTags(tags = Array(TaskTag.RequiresTmdbApi))
abstract class ChangesDumpTask(
  thingType: ItemType,
  itemExpander: TmdbItemLookup,
  protected val publisher: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[ChangesDumpFileRow, Int] {
  override protected def getRawJson(currentId: Int): Future[String] = {
    val extraFields = thingType match {
      case ItemType.Movie =>
        List("recommendations", "similar", "videos")
      case ItemType.Show =>
        List("recommendations", "similar", "videos")
      case ItemType.Person =>
        List("tagged_images")
    }

    itemExpander.expandRaw(thingType, currentId, extraFields).map(_.noSpaces)
  }

  override protected def getCurrentId(item: ChangesDumpFileRow): Int = item.id

  override protected def baseFileName: String = s"$thingType-delta"
}

@TaskTags(tags = Array(TaskTag.RequiresTmdbApi))
class MovieChangesDumpTask @Inject()(
  itemExpander: TmdbItemLookup,
  publisher: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ItemType.Movie, itemExpander, publisher) {
  override def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] = {
    TeletrackerTask.taskMessage[ImportMoviesFromDump](
      ImportTmdbDumpTaskArgs.default(s3Uri)
    ) :: Nil
  }
}

@TaskTags(tags = Array(TaskTag.RequiresTmdbApi))
class TvChangesDumpTask @Inject()(
  itemExpander: TmdbItemLookup,
  publisher: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ItemType.Show, itemExpander, publisher) {
  override def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] = {
    TeletrackerTask.taskMessage[ImportTvShowsFromDump](
      ImportTmdbDumpTaskArgs.default(s3Uri)
    ) :: Nil
  }
}

@TaskTags(tags = Array(TaskTag.RequiresTmdbApi))
class PersonChangesDumpTask @Inject()(
  itemExpander: TmdbItemLookup,
  publisher: SqsAsyncClient
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ItemType.Person, itemExpander, publisher) {
  override def followupTasksToSchedule(): List[TeletrackerTaskQueueMessage] = {
    TeletrackerTask.taskMessage[ImportPeopleFromDump](
      ImportTmdbDumpTaskArgs.default(s3Uri)
    ) :: Nil
  }
}

@JsonCodec
case class ChangesDumpFileRow(
  id: Int,
  adult: Option[Boolean])
    extends TmdbDumpFileRow {
  override def popularity: Double = 0.0
}
