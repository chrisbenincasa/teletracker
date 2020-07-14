package com.teletracker.common.model.scraping.netflix

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

object NetflixOriginalScrapeItem {
  implicit final val availabilityDetails
    : ScrapedItemAvailabilityDetails[NetflixOriginalScrapeItem] =
    new ScrapedItemAvailabilityDetails[NetflixOriginalScrapeItem] {
      override def offerType(t: NetflixOriginalScrapeItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: NetflixOriginalScrapeItem): Option[String] =
        t.externalId

      override def externalIds(
        t: NetflixOriginalScrapeItem
      ): Map[ExternalSource, String] =
        uniqueKey(t)
          .map(key => Map(ExternalSource.Netflix -> key))
          .getOrElse(Map.empty)
    }
}

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

  override def description: Option[String] = None

  override def itemType: ItemType = `type`
}
