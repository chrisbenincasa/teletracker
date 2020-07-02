package com.teletracker.common.model.scraping.hbo

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  ScrapedCastMember,
  ScrapedCrewMember,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.{Instant, ZoneOffset}

object HboScrapedCatalogItem {
  implicit final val scrapedItemAvailabilityDetails
    : ScrapedItemAvailabilityDetails[HboScrapedCatalogItem] =
    new ScrapedItemAvailabilityDetails[HboScrapedCatalogItem] {
      override def offerType(t: HboScrapedCatalogItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: HboScrapedCatalogItem): Option[String] =
        t.externalId

      override def externalIds(
        t: HboScrapedCatalogItem
      ): Map[ExternalSource, String] = {
        Map(
          ExternalSource.HboGo -> t.externalId,
          ExternalSource.HboMax -> t.externalId
        ).collect {
          case (source, Some(str)) => source -> str
        }
      }
    }
}

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
