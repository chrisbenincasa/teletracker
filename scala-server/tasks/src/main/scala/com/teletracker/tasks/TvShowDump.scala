package com.teletracker.tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.process.tmdb.ItemExpander
import javax.inject.Inject
import io.circe.syntax._
import scala.concurrent.{ExecutionContext, Future}

object TvShowDumpTool extends DataDumpTaskApp[MovieDump]

class TvShowDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask(storage) {

  override protected val baseFileName = "shows"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandMovie(currentId, List("recommendations", "similar"))
      .map(_.asJson.noSpaces)
  }
}
