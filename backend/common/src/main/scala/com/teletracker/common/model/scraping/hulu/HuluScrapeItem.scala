package com.teletracker.common.model.scraping.hulu

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import io.circe.generic.JsonCodec

object HuluScrapeItem {
  implicit final val availabilityDetails
    : ScrapedItemAvailabilityDetails[HuluScrapeItem] =
    new ScrapedItemAvailabilityDetails[HuluScrapeItem] {
      override def offerType(t: HuluScrapeItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: HuluScrapeItem): Option[String] =
        t.externalId

      override def externalIds(t: HuluScrapeItem): Map[ExternalSource, String] =
        uniqueKey(t)
          .map(key => Map(ExternalSource.Hulu -> key))
          .getOrElse(Map.empty)
    }
}

@JsonCodec
case class HuluScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  notes: String,
  category: Option[String],
  network: String,
  status: String,
  externalId: Option[String],
  description: Option[String],
  `type`: ItemType)
    extends ScrapedItem {
  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean =
    !isMovie || category.getOrElse("").toLowerCase().contains("series")

  override def itemType: ItemType = `type`
}

@JsonCodec
case class HuluSearchResponse(groups: List[HuluSearchResponseGroup])

@JsonCodec
case class HuluSearchResponseGroup(results: List[HuluSearchResponseResult])

@JsonCodec
case class HuluSearchResponseResult(
  entity_metadata: Option[HuluSearchResultMetadata],
  metrics_info: Option[HuluSearchMetricsInfo])

@JsonCodec
case class HuluSearchResultMetadata(
  premiere_date: Option[String],
  target_name: String)

@JsonCodec
case class HuluSearchMetricsInfo(target_type: String)
