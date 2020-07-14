package com.teletracker.tasks.scraper.model

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  PartialEsItem,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.Codec

object PotentialInput {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec.AsObject[PotentialInput[T]] =
    io.circe.generic.semiauto.deriveCodec[PotentialInput[T]]

  implicit def availabilityDetails[T <: ScrapedItem](
    implicit underlyingAvailabilityDetails: ScrapedItemAvailabilityDetails[T]
  ): ScrapedItemAvailabilityDetails[PotentialInput[T]] =
    new ScrapedItemAvailabilityDetails[PotentialInput[T]] {
      override def offerType(t: PotentialInput[T]): OfferType =
        underlyingAvailabilityDetails.offerType(t.scraped)
      override def uniqueKey(t: PotentialInput[T]): Option[String] =
        underlyingAvailabilityDetails.uniqueKey(t.scraped)
      override def externalIds(
        t: PotentialInput[T]
      ): Map[ExternalSource, String] =
        underlyingAvailabilityDetails.externalIds(t.scraped)
    }
}

case class PotentialInput[T <: ScrapedItem: Codec](
  potential: PartialEsItem,
  scraped: T)
    extends ScrapedItem {
  override def availableDate: Option[String] = scraped.availableDate
  override def title: String = scraped.title
  override def releaseYear: Option[Int] = scraped.releaseYear
  override def category: Option[String] = scraped.category
  override def network: String = scraped.network
  override def status: String = scraped.status
  override def externalId: Option[String] = scraped.externalId
  override def isMovie: Boolean = scraped.isMovie
  override def isTvShow: Boolean = scraped.isTvShow
  override def description: Option[String] = scraped.description
  override def itemType: ItemType = scraped.itemType
}
