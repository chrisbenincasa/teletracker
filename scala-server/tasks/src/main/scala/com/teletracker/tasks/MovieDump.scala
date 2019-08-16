package com.teletracker.tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object MovieDumpTool extends DataDumpTaskApp[MovieDump]

class MovieDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask(storage) {

  override protected val baseFileName: String = "movies"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandMovie(currentId, List("recommendations", "similar"))
      .map(_.asJson.noSpaces)
  }
}
