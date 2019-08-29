package com.teletracker.tasks.tmdb.export_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object MovieDumpTool extends DataDumpTaskApp[MovieDump]

class MovieDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[MovieDumpFileRow](storage) {

  implicit override protected val tDecoder: Decoder[MovieDumpFileRow] =
    deriveCodec

  override protected val baseFileName: String = "movies"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandMovie(currentId, List("recommendations", "similar"))
      .map(_.asJson.noSpaces)
  }
}
