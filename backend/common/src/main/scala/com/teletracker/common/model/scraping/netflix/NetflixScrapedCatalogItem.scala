package com.teletracker.common.model.scraping.netflix

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class NetflixScrapedCatalogItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  itemType: ItemType,
  externalId: Option[String],
  description: Option[String],
  seasons: Option[List[NetflixScrapedSeason]])
    extends ScrapedItem {
  val status = "Available"

  override def category: Option[String] = None

  override def isMovie: Boolean = itemType == ItemType.Movie

  override def isTvShow: Boolean = itemType == ItemType.Show

  override def url: Option[String] =
    externalId.map(id => s"https://netflix.com/title/$id")

  override def numSeasonsAvailable: Option[Int] = seasons.map(_.size)
}

@JsonCodec
case class NetflixScrapedSeason(
  seasonNumber: Int,
  releaseYear: Option[Int],
  description: Option[String],
  episodes: Option[List[NetflixScrapedEpisode]])

@JsonCodec
case class NetflixScrapedEpisode(
  seasonNumber: Int,
  episodeNumber: Int,
  name: String,
  runtime: String,
  description: Option[String])
