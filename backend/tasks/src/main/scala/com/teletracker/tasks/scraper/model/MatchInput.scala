package com.teletracker.tasks.scraper.model

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.Codec

object MatchInput {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec[MatchInput[T]] =
    io.circe.generic.semiauto.deriveCodec[MatchInput[T]]

  implicit def availabilityDetails[T <: ScrapedItem](
    implicit underlyingAvailabilityDetails: ScrapedItemAvailabilityDetails[T]
  ): ScrapedItemAvailabilityDetails[MatchInput[T]] =
    new ScrapedItemAvailabilityDetails[MatchInput[T]] {
      override def offerType(t: MatchInput[T]): OfferType =
        underlyingAvailabilityDetails.offerType(t.scrapedItem)
      override def uniqueKey(t: MatchInput[T]): Option[String] =
        underlyingAvailabilityDetails.uniqueKey(t.scrapedItem)
      override def externalIds(t: MatchInput[T]): Map[ExternalSource, String] =
        underlyingAvailabilityDetails.externalIds(t.scrapedItem)
    }
}

case class MatchInput[T <: ScrapedItem: Codec](
  scrapedItem: T,
  esItem: EsItem)
    extends ScrapedItem {
  override def availableDate: Option[String] = scrapedItem.availableDate
  override def title: String = scrapedItem.title
  override def releaseYear: Option[Int] = scrapedItem.releaseYear
  override def category: Option[String] = scrapedItem.category
  override def network: String = scrapedItem.network
  override def status: String = scrapedItem.status
  override def externalId: Option[String] = scrapedItem.externalId
  override def description: Option[String] = scrapedItem.description
  override def isMovie: Boolean = scrapedItem.isMovie
  override def isTvShow: Boolean = scrapedItem.isTvShow
  override def itemType: ItemType = scrapedItem.itemType
}
