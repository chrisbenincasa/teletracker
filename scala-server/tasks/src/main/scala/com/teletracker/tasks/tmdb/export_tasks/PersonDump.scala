package com.teletracker.tasks.tmdb.export_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object PersonDumpTool extends DataDumpTaskApp[PersonDump]

class PersonDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[PersonDumpFileRow](storage) {

  implicit override protected val tDecoder: Decoder[PersonDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "people"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandPerson(currentId)
      .map(_.asJson.noSpaces)
  }
}

case class PersonDumpFileRow(
  adult: Boolean,
  id: Int,
  name: String,
  popularity: Double)
    extends TmdbDumpFileRow
