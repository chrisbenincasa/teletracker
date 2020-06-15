package com.teletracker.common.model.scraping.hbo

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.{
  ScrapedCastMember,
  ScrapedCrewMember,
  ScrapedItem
}
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
  releaseDate: Option[String],
  releaseYear: Option[Int],
  override val cast: Option[Seq[HboScrapedCastMember]],
  override val crew: Option[Seq[HboScrapedCrewMember]])
    extends ScrapedItem {
  override def availableDate: Option[String] = None
  override def category: Option[String] = None
  override def status: String = ""
  override def externalId: Option[String] = id
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
}

@JsonCodec
case class HboScrapedCastMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCastMember

@JsonCodec
case class HboScrapedCrewMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCrewMember
