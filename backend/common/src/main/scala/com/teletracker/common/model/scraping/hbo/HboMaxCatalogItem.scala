package com.teletracker.common.model.scraping.hbo

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.{LocalDate, OffsetDateTime}

@JsonCodec
case class HboMaxCatalogItem(
  id: String,
  externalId: Option[String],
  itemType: ItemType,
  description: Option[String],
  title: String,
  override val url: Option[String],
  couldBeOnHboGo: Option[Boolean])
    extends ScrapedItem {
  override def releaseYear: Option[Int] = None

  override def category: Option[String] = None

  override def status: String = ""

  override def availableDate: Option[String] = None

  override def isMovie: Boolean = itemType == ItemType.Movie

  override def isTvShow: Boolean = itemType == ItemType.Show

  override def network: String = "hbo-max"

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
}
