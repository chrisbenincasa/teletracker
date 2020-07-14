package com.teletracker.common.model.scraping.hbo

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

object HboMaxScrapedCatalogItem {
  implicit final val availabilityDetails
    : ScrapedItemAvailabilityDetails[HboMaxScrapedCatalogItem] =
    new ScrapedItemAvailabilityDetails[HboMaxScrapedCatalogItem] {
      override def offerType(t: HboMaxScrapedCatalogItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: HboMaxScrapedCatalogItem): Option[String] =
        t.externalId

      override def externalIds(
        t: HboMaxScrapedCatalogItem
      ): Map[ExternalSource, String] =
        Map(
          ExternalSource.HboMax -> t.externalId
        ).collect {
          case (source, Some(str)) => source -> str
        }
    }
}

@JsonCodec
case class HboMaxScrapedCatalogItem(
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
}
