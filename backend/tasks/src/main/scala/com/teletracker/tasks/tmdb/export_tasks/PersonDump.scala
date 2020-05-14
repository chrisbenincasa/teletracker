package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.tasks.model.PersonDumpFileRow
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object PersonDumpTool extends DataDumpTaskApp[PersonDump]

class PersonDump @Inject()(
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[PersonDumpFileRow, Int] {

  implicit override protected val tDecoder: Decoder[PersonDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "people"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandPersonRaw(currentId)
      .map(_.noSpaces)
  }

  override protected def getCurrentId(item: PersonDumpFileRow): Int = item.id
}
