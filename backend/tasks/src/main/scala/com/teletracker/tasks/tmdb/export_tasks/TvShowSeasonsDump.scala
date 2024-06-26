package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.TmdbItemLookup
import io.circe.Decoder
import io.circe.syntax._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

import TvShowSeasonDumpRow._

class TvShowSeasonsDump @Inject()(
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowSeasonDumpRow, (Int, Int)] {

  override protected def getRawJson(currentId: (Int, Int)): Future[String] = {
    itemExpander
      .expandTvSeasonRaw(currentId._1, currentId._2)
      .map(seasonJson => {
        Map(
          "showId" -> currentId._1.asJson,
          "season" -> seasonJson
        ).asJson.noSpaces
      })
  }

  override protected def getCurrentId(item: TvShowSeasonDumpRow): (Int, Int) = {
    (item.showId, item.seasonNumber)
  }

  override protected def baseFileName: String = "show_seasons"
}

@JsonCodec
case class TvShowSeasonDumpRow(
  showId: Int,
  seasonNumber: Int)
