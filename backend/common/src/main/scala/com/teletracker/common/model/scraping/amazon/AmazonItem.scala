package com.teletracker.common.model.scraping.amazon

import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  OfferType,
  PresentationType
}
import com.teletracker.common.model.scraping.{
  ScrapedCastMember,
  ScrapedCrewMember,
  ScrapedItem,
  ScrapedItemAvailabilityDetails,
  ScrapedOffer
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import java.util.UUID

object AmazonItem {
  implicit val availabilityDetails: ScrapedItemAvailabilityDetails[AmazonItem] =
    new ScrapedItemAvailabilityDetails[AmazonItem] {
      override def offerType(t: AmazonItem): OfferType = ???
      override def uniqueKey(t: AmazonItem): Option[String] = t.externalId
      override def externalIds(t: AmazonItem): Map[ExternalSource, String] =
        t.externalId
          .map(id => Map(ExternalSource.AmazonVideo -> id))
          .getOrElse(Map.empty)
    }
}

@ConfiguredJsonCodec
case class AmazonItem(
  id: Option[String],
  title: String,
  externalId: Option[String],
  description: Option[String],
  itemType: ItemType,
  network: String,
  override val url: Option[String],
  releaseDate: Option[String],
  releaseYear: Option[Int],
  override val cast: Option[List[AmazonItemCastMember]],
  override val crew: Option[List[AmazonItemCrewMember]],
  runtime: Option[String],
  availableOnPrime: Boolean,
  override val offers: Option[List[AmazonItemOffer]],
  override val internalId: Option[UUID],
  override val version: Long = -1)
    extends ScrapedItem {
  override def availableDate: Option[String] = None

  override def category: Option[String] = None

  override def status: String = ""
}

@ConfiguredJsonCodec
case class AmazonItemCastMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCastMember

@ConfiguredJsonCodec
case class AmazonItemCrewMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCrewMember

@ConfiguredJsonCodec
case class AmazonItemOffer(
  offerType: OfferType,
  price: Option[Double],
  currency: Option[String],
  quality: Option[PresentationType])
    extends ScrapedOffer
