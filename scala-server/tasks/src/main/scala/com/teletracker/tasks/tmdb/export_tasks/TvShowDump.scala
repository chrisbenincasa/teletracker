package com.teletracker.tasks.tmdb.export_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object TvShowDumpTool extends DataDumpTaskApp[TvShowDump]

class TvShowDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowDumpFileRow](storage) {

  implicit override protected val tDecoder: Decoder[TvShowDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "shows"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandTvShow(currentId, List("recommendations", "similar"))
      .map(_.asJson.noSpaces)
  }
}
