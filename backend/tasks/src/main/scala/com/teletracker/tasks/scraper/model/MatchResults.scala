package com.teletracker.tasks.scraper.model

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Codec
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

@JsonCodec
case class MatchResult[T <: ScrapedItem](
  scrapedItem: T,
  esItem: EsItem) {
  def toSerializable: SerializableMatchResult[T] =
    SerializableMatchResult(scrapedItem, PartialEsItem.forEsItem(esItem))
}

object SerializableMatchResult {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec[SerializableMatchResult[T]] =
    io.circe.generic.semiauto.deriveCodec[SerializableMatchResult[T]]
}

case class SerializableMatchResult[T <: ScrapedItem](
  scrapedItem: T,
  esItem: PartialEsItem)

case class NonMatchResult[T <: ScrapedItem](
  amendedScrapedItem: T,
  originalScrapedItem: T,
  esItem: EsItem) {
  def toMatchResult: MatchResult[T] =
    MatchResult(amendedScrapedItem, esItem)

  def toSerializable: SerializableNonMatchResult[T] =
    SerializableNonMatchResult(
      amendedScrapedItem,
      originalScrapedItem,
      PartialEsItem.forEsItem(esItem)
    )
}

object SerializableNonMatchResult {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec[SerializableNonMatchResult[T]] =
    io.circe.generic.semiauto.deriveCodec[SerializableNonMatchResult[T]]
}

case class SerializableNonMatchResult[T <: ScrapedItem](
  amendedScrapedItem: T,
  originalScrapedItem: T,
  esItem: PartialEsItem)

object PotentialMatch {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec[PotentialMatch[T]] =
    io.circe.generic.semiauto.deriveCodec[PotentialMatch[T]]

  def forEsItem[T <: ScrapedItem](
    esItem: EsItem,
    scrapedItem: T
  ): PotentialMatch[T] = {
    PotentialMatch(
      PartialEsItem.forEsItem(esItem),
      scrapedItem
    )
  }
}

case class PotentialMatch[+T <: ScrapedItem](
  potential: PartialEsItem,
  scraped: T)

object PartialEsItem {
  implicit val codec: Codec[PartialEsItem] =
    io.circe.generic.semiauto.deriveCodec[PartialEsItem]

  def forEsItem(esItem: EsItem) = {
    PartialEsItem(
      id = esItem.id,
      original_title = esItem.original_title,
      title = esItem.title.get.head,
      release_date = esItem.release_date,
      external_ids = esItem.external_ids.map(_.map(_.toString)),
      `type` = esItem.`type`,
      slug = esItem.slug
    )
  }
}

case class PartialEsItem(
  id: UUID,
  original_title: Option[String],
  title: String,
  release_date: Option[LocalDate],
  external_ids: Option[List[String]],
  `type`: ItemType,
  slug: Option[Slug])
