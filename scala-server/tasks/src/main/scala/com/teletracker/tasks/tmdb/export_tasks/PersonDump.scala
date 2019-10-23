package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}

object PersonDumpTool extends DataDumpTaskApp[PersonDump]

class PersonDump @Inject()(
  s3: S3Client,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[PersonDumpFileRow](s3) {

  implicit override protected val tDecoder: Decoder[PersonDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "people"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandPersonRaw(currentId)
      .map(_.noSpaces)
  }
}

case class PersonDumpFileRow(
  adult: Boolean,
  id: Int,
  name: String,
  popularity: Double)
    extends TmdbDumpFileRow
