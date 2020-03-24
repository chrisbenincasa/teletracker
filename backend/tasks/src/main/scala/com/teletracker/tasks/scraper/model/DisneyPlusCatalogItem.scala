package com.teletracker.tasks.scraper.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.ScrapedItem
import io.circe.generic.JsonCodec

@JsonCodec
case class DisneyPlusCatalogItem(
  title: String,
  releaseYear: Option[Int],
  override val thingType: Option[ItemType] = None)
    extends ScrapedItem {
  override def availableDate: Option[String] = None

  override def category: String = ""

  override def network: String = "disney-plus"

  override def status: String = "Available"

  override def externalId: Option[String] = None

  override def isMovie: Boolean = false

  override def isTvShow: Boolean = false
}
