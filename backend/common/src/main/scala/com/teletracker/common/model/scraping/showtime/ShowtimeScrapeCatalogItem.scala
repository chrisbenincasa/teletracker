package com.teletracker.common.model.scraping.showtime

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class ShowtimeScrapeCatalogItem(
  id: String,
  title: String,
  externalId: Option[String],
  description: Option[String],
  itemType: ItemType,
  network: String,
  override val url: Option[String],
  seasons: Option[List[ShowtimeScrapeSeasonItem]])
    extends ScrapedItem {
  override val availableDate: Option[String] = None
  override def releaseYear: Option[Int] = ???
  override val category: Option[String] = None
  override def status: String = ""
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
}

@JsonCodec
case class ShowtimeScrapeSeasonItem(
  seasonNumber: Int,
  releaseDate: Int,
  description: Option[String],
  episodes: Option[List[ShowtimeScrapeEpisodeItem]])

@JsonCodec
case class ShowtimeScrapeEpisodeItem(
  episodeNumber: Int,
  releaseDate: Option[String],
  description: Option[String])
