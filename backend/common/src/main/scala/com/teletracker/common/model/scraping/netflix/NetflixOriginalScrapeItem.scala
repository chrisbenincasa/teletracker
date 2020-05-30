package com.teletracker.common.model.scraping.netflix

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class NetflixOriginalScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  status: String,
  `type`: ItemType,
  externalId: Option[String])
    extends ScrapedItem {
  override def category: Option[String] = None

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override def description: Option[String] = None
}
