package com.teletracker.common.model.scraping.hulu

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}

@JsonCodec
case class HuluScrapeCatalogItem(
  id: String,
  availableOn: Option[String],
  expiresOn: Option[String],
  title: String,
  premiereDate: Option[String],
  network: String,
  itemType: ItemType,
  externalId: Option[String],
  genres: Option[List[String]],
  description: Option[String],
  additionalServiceRequired: Option[String],
  episodes: Option[List[HuluScrapeEpisode]])
    extends ScrapedItem {
  override def category: Option[String] = None
  override def status: String = ""
  override def availableDate: Option[String] = availableOn
  override lazy val releaseYear: Option[Int] =
    premiereDate.map(Instant.parse(_).atOffset(ZoneOffset.UTC).getYear)
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show

  override def numSeasonsAvailable: Option[Int] = {
    episodes
      .filter(_.nonEmpty)
      .map(episodes => episodes.map(_.seasonNumber).distinct.size)
  }

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
  override def url: Option[String] = {
    externalId.map(eid => {
      s"https://www.hulu.com/${makeHuluType(itemType)}/${makeHuluSlug(title, eid)}"
    })
  }

  private def makeHuluType(thingType: ItemType) = {
    thingType match {
      case ItemType.Movie  => "movie"
      case ItemType.Show   => "series"
      case ItemType.Person => throw new IllegalArgumentException
    }
  }

  private def makeHuluSlug(
    title: String,
    id: String
  ) = {
    Slug.apply(title, None).addSuffix(id).toString
  }
}

@JsonCodec
case class HuluScrapeEpisode(
  id: String,
  externalId: String,
  genres: List[String],
  description: Option[String],
  title: String,
  rating: Option[String],
  episodeNumber: Int,
  seasonNumber: Int,
  premiereDate: Option[String],
  duration: Option[Int])
