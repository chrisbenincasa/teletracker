package com.teletracker.tasks.tmdb.export_tasks

import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.storage.Storage
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.tasks.SchedulesFollowupTasks
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
import scala.concurrent.{ExecutionContext, Future}

abstract class ChangesDumpTask(
  thingType: ThingType,
  storage: Storage,
  itemExpander: ItemExpander,
  protected val publisher: Publisher
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[ChangesDumpFileRow](storage)
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
  storage: Storage,
  itemExpander: ItemExpander,
  publisher: Publisher
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Movie, storage, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportMoviesFromDump](
      ImportTmdbDumpTaskArgs(
        input = googleStorageUri
      )
    ) :: Nil
  }
}

class TvChangesDumpTask @Inject()(
  storage: Storage,
  itemExpander: ItemExpander,
  publisher: Publisher
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Show, storage, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportTvShowsFromDump](
      ImportTmdbDumpTaskArgs(
        input = googleStorageUri
      )
    ) :: Nil
  }
}

class PersonChangesDumpTask @Inject()(
  storage: Storage,
  itemExpander: ItemExpander,
  publisher: Publisher
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Person, storage, itemExpander, publisher) {
  override def followupTasksToSchedule(
    args: DataDumpTaskArgs
  ): List[TeletrackerTaskQueueMessage] = {
    TaskMessageHelper.forTask[ImportPeopleFromDump](
      ImportTmdbDumpTaskArgs(
        input = googleStorageUri
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
