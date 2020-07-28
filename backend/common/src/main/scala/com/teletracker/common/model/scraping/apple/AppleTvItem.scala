package com.teletracker.common.model.scraping.apple

import com.teletracker.common.db.model.{ItemType, OfferType, PresentationType}
import com.teletracker.common.model.scraping.{
  ScrapedCastMember,
  ScrapedCrewMember,
  ScrapedItem,
  ScrapedOffer
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class AppleTvItem(
  id: Option[String],
  title: String,
  externalId: Option[String],
  description: Option[String],
  itemType: ItemType,
  network: String,
  override val url: Option[String],
  releaseDate: Option[String],
  releaseYear: Option[Int],
  override val cast: Option[List[AppleTvItemCastMember]],
  override val crew: Option[List[AppleTvItemCrewMember]],
  runtime: Option[Int],
  override val offers: Option[List[AppleTvItemOffer]])
    extends ScrapedItem {
  override def availableDate: Option[String] = None

  override def category: Option[String] = None

  override def status: String = ""
}

@JsonCodec
case class AppleTvItemCastMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCastMember

@JsonCodec
case class AppleTvItemCrewMember(
  name: String,
  order: Option[Int],
  role: Option[String])
    extends ScrapedCrewMember

@JsonCodec
case class AppleTvItemOffer(
  offerType: OfferType,
  price: Option[Double],
  currency: Option[String],
  quality: Option[PresentationType])
    extends ScrapedOffer
