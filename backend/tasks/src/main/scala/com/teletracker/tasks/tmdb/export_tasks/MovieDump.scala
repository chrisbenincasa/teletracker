package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}

object MovieDumpTool extends DataDumpTaskApp[MovieDump]

class MovieDump @Inject()(
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[MovieDumpFileRow, Int] {

  implicit override protected val tDecoder: Decoder[MovieDumpFileRow] =
    deriveCodec

  override protected val baseFileName: String = "movies"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandMovieRaw(currentId, List("recommendations", "similar", "videos"))
      .map(_.noSpaces)
  }

  override protected def getCurrentId(item: MovieDumpFileRow): Int = item.id
}
