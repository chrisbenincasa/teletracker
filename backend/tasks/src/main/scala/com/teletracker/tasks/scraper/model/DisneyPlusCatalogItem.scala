package com.teletracker.tasks.scraper.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.ScrapedItem
import io.circe.generic.JsonCodec

@JsonCodec
case class DisneyPlusCatalogItem(
  name: String,
  releaseDate: Option[Int],
  `type`: ItemType,
  externalId: Option[String],
  description: Option[String],
  override val url: Option[String])
    extends ScrapedItem {

  override def title: String = name

  override def availableDate: Option[String] = None

  override def category: Option[String] = None

  override def network: String = "disney-plus"

  override def status: String = "Available"

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override def releaseYear: Option[Int] = releaseDate
}
