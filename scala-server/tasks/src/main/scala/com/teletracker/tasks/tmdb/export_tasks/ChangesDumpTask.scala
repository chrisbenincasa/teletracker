package com.teletracker.tasks.tmdb.export_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

abstract class ChangesDumpTask(
  thingType: ThingType,
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[ChangesDumpFileRow](storage) {
  implicit override protected val tDecoder: Decoder[ChangesDumpFileRow] =
    deriveCodec

  override protected def getRawJson(currentId: Int): Future[String] = {
    val extraFields = thingType match {
      case ThingType.Movie =>
        List("recommendations", "similar")
      case ThingType.Show =>
        List("recommendations", "similar")
      case ThingType.Person =>
        Nil
    }

    itemExpander.expandRaw(thingType, currentId, extraFields).map(_.noSpaces)
  }

  override protected def baseFileName: String = s"$thingType-delta"
}

class MovieChangesDumpTask @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Movie, storage, itemExpander)

class TvChangesDumpTask @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Show, storage, itemExpander)

class PersonChangesDumpTask @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends ChangesDumpTask(ThingType.Person, storage, itemExpander)

case class ChangesDumpFileRow(
  id: Int,
  adult: Option[Boolean])
    extends TmdbDumpFileRow {
  override def popularity: Double = 0.0
}
