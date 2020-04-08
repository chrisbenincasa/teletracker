package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.ItemExpander
import io.circe.Decoder
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

import TvShowEpisodeDumpRow._

class TvShowEpisodesDump @Inject()(
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[TvShowEpisodeDumpRow, (Int, Int)] {

  implicit override protected lazy val tDecoder: Decoder[TvShowEpisodeDumpRow] =
    implicitly[Decoder[TvShowEpisodeDumpRow]]

  override protected def getRawJson(currentId: (Int, Int)): Future[String] = {
    itemExpander.expandTvSeasonRaw(currentId._1, currentId._2).map(_.noSpaces)
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
