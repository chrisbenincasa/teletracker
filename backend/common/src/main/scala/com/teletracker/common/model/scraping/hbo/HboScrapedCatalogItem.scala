package com.teletracker.common.model.scraping.hbo

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.{Instant, ZoneOffset}

@JsonCodec
case class HboScrapedCatalogItem(
  title: String,
  description: Option[String],
  itemType: ItemType,
  id: Option[String],
  goUrl: Option[String],
  nowUrl: Option[String],
  network: String,
  releaseDate: Option[String])
    extends ScrapedItem {
  override def availableDate: Option[String] = None
  override lazy val releaseYear: Option[Int] =
    releaseDate.map(Instant.parse(_).atOffset(ZoneOffset.UTC).getYear)
  override def category: Option[String] = None
  override def status: String = ""
  override def externalId: Option[String] = id
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
}
