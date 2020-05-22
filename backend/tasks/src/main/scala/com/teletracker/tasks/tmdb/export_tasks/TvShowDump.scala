package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.tasks.model.TvShowDumpFileRow
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object TvShowDumpTool extends DataDumpTaskApp[TvShowDump]

class TvShowDump @Inject()(
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowDumpFileRow, Int] {

  implicit override protected val tDecoder: Decoder[TvShowDumpFileRow] =
    deriveCodec

  override protected val baseFileName = "shows"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandRaw(
        ItemType.Show,
        currentId,
        List("recommendations", "similar", "videos")
      )
      .map(_.asJson.noSpaces)
  }

  override protected def getCurrentId(item: TvShowDumpFileRow): Int = item.id
}
