package com.teletracker.common.model.scraping.disney

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.LocalDate

@JsonCodec
case class DisneyPlusCatalogItem(
  id: String,
  title: String,
  releaseDate: Option[LocalDate],
  releaseYear: Option[Int],
  itemType: ItemType,
  description: Option[String],
  slug: Option[String],
  override val url: Option[String])
    extends ScrapedItem {
  override def externalId: Option[String] = Some(id)

  override def availableDate: Option[String] = None

  override def category: Option[String] = None

  override def network: String = "disney-plus"

  override def status: String = "Available"

  override def isMovie: Boolean = itemType == ItemType.Movie

  override def isTvShow: Boolean = itemType == ItemType.Show
}
