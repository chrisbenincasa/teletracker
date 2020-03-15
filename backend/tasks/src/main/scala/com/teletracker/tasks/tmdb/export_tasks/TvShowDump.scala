package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}

object TvShowDumpTool extends DataDumpTaskApp[TvShowDump]

class TvShowDump @Inject()(
  s3: S3Client,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowDumpFileRow](s3) {

  implicit override protected val tDecoder: Decoder[TvShowDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "shows"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandRaw(
        ThingType.Show,
        currentId,
        List("recommendations", "similar", "videos")
      )
      .map(_.asJson.noSpaces)
  }
}