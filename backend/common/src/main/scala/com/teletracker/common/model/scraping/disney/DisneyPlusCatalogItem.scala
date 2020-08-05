package com.teletracker.common.model.scraping.disney

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.model.scraping.{
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.text.Normalizer
import java.time.LocalDate
import java.util.regex.Pattern

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

  final private val NonLatin = Pattern.compile("[^\\w\\s-]")
  final private val MultipleSpaces = Pattern.compile("\\s+")

  def createSlug(title: String) =
    Normalizer
    // Replace diacritics and non-breaking spaces
      .normalize(title, Normalizer.Form.NFD)
      .replaceAll("[\u0300-\u036f\u00a0]", "")
      // Replace characters that aren't included as a hyphenated split
      .replaceAll("[\"\'’.!–]+", "")
      // Split on characters that create hyphenated split
      .split("[\\s()@&.?$+,/:-]+")
      .map(_.trim)
      .filter(_.nonEmpty)
      // Remove non-Latin characters
      .filterNot(NonLatin.matcher(_).matches)
      // Replace N spaces with 1 space
      .map(MultipleSpaces.matcher(_).replaceAll(" "))
      .map(_.toLowerCase)
      .mkString("-")
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
  override def externalId: Option[String] =
    Some(DisneyPlusCatalogItem.createSlug(title) + "/" + id)

  override def availableDate: Option[String] = None

  override def category: Option[String] = None

  override def network: String = "disney-plus"

  override def status: String = "Available"

  override def isMovie: Boolean = itemType == ItemType.Movie

  override def isTvShow: Boolean = itemType == ItemType.Show
}
