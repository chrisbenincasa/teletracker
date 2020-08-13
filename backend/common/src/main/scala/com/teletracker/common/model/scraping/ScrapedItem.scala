package com.teletracker.common.model.scraping

import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  OfferType,
  PresentationType
}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

trait ScrapedItem {
  def availableDate: Option[String]
  def title: String
  def releaseYear: Option[Int]
  def category: Option[String]
  def network: String
  def status: String
  def externalId: Option[String]
  def description: Option[String]
  def url: Option[String] = None
  def numSeasonsAvailable: Option[Int] = None
  def posterImageUrl: Option[String] = None
  def actualItemId: Option[UUID] = None
  def version: Option[Long] = None

  // Populated if the scraper was able to make an association with an internal ID, without matching.
  def internalId: Option[UUID] = None

  lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean = itemType == ItemType.Movie
  def isTvShow: Boolean = itemType == ItemType.Show
  def itemType: ItemType

  def cast: Option[Seq[ScrapedCastMember]] = None
  def crew: Option[Seq[ScrapedCrewMember]] = None

  def offers: Option[List[ScrapedOffer]] = None
}

trait ScrapedCastMember {
  def name: String
  def order: Option[Int]
  def role: Option[String]
}

trait ScrapedCrewMember {
  def name: String
  def order: Option[Int]
  def role: Option[String]
}

trait ScrapedOffer {
  def offerType: OfferType
  def price: Option[Double]
  def currency: Option[String]
  def quality: Option[PresentationType]
}

object ScrapedItemAvailabilityDetails {
  object syntax extends ScrapedItemAvailabilityDetailsSyntax
}

trait ScrapedItemAvailabilityDetailsSyntax {
  implicit def toDetailOps[T <: ScrapedItem](
    t: T
  ): ScrapedItemAvailabilityDetailsOps[T] =
    new ScrapedItemAvailabilityDetailsOps[T](t)
}

trait ScrapedItemAvailabilityDetails[T <: ScrapedItem] {
  def uniqueKey(t: T): Option[String]
  def externalIds(t: T): Map[ExternalSource, String]
}

class ScrapedItemAvailabilityDetailsOps[T <: ScrapedItem](val item: T)
    extends AnyVal {
  def uniqueKey(
    implicit details: ScrapedItemAvailabilityDetails[T]
  ): Option[String] =
    details.uniqueKey(item)

  def externalIds(
    implicit details: ScrapedItemAvailabilityDetails[T]
  ): Map[ExternalSource, String] =
    details.externalIds(item)
}
