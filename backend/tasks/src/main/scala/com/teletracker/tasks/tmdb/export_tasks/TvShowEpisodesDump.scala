package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.tasks.tmdb.export_tasks.TvShowEpisodeDumpRow._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TvShowEpisodesDump @Inject()(
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowEpisodeDumpRow, (Int, Int)] {

  override protected def getRawJson(currentId: (Int, Int)): Future[String] = {
    itemExpander
      .expandTvSeasonRaw(currentId._1, currentId._2)
      .map(episodeJson => {
        Map(
          "showId" -> currentId._1.asJson,
          "season" -> currentId._2.asJson,
          "episode" -> episodeJson
        ).asJson.noSpaces
      })
  }

  override protected def getCurrentId(
    item: TvShowEpisodeDumpRow
  ): (Int, Int) = {
    (item.showId, item.seasonNumber)
  }

  override protected def baseFileName: String = "show_seasons"
}

@JsonCodec
case class TvShowEpisodeDumpRow(
  showId: Int,
  seasonNumber: Int,
  episodeNumber: Int)
