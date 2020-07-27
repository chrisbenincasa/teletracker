package com.teletracker.common.model.scraping.google

import com.teletracker.common.db.model.{ItemType, OfferType}
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class GooglePlayStoreItem(
  id: Option[String],
  title: String,
  releaseYear: Option[Int],
  description: Option[String],
  externalId: Option[String],
  override val itemType: ItemType,
  override val network: String,
  override val posterImageUrl: Option[String],
  offers: Option[List[GooglePlayStoreOffer]])
    extends ScrapedItem {
  override def availableDate: Option[String] = None
  override def category: Option[String] = None
  override def status: String = "Available"
}

@JsonCodec
case class GooglePlayStoreOffer(
  offerType: OfferType,
  price: Double,
  quality: Option[String],
  currency: Option[String])
