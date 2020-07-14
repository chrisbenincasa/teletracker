package com.teletracker.common.model.scraping.disney

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.LocalDate

object DisneyPlusCatalogItem {
  implicit final val availabilityDetails
    : ScrapedItemAvailabilityDetails[DisneyPlusCatalogItem] =
    new ScrapedItemAvailabilityDetails[DisneyPlusCatalogItem] {
      override def offerType(t: DisneyPlusCatalogItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: DisneyPlusCatalogItem): Option[String] =
        t.externalId

      override def externalIds(
        t: DisneyPlusCatalogItem
      ): Map[ExternalSource, String] =
        uniqueKey(t)
          .map(key => Map(ExternalSource.DisneyPlus -> key))
          .getOrElse(Map.empty)
    }
}

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
