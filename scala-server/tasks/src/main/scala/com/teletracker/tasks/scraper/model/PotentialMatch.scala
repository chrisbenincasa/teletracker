package com.teletracker.tasks.scraper.model

import com.teletracker.common.elasticsearch.EsItem
import com.teletracker.tasks.scraper.ScrapedItem
import io.circe.Codec
import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

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
      PotentialEsItem(
        id = esItem.id,
        original_title = esItem.original_title,
        title = esItem.title.get,
        release_date = esItem.release_date,
        external_ids = esItem.external_ids.map(_.map(_.toString))
      ),
      scrapedItem
    )
  }
}

case class PotentialMatch[T <: ScrapedItem](
  potential: PotentialEsItem,
  scraped: T)

@JsonCodec
case class PotentialEsItem(
  id: UUID,
  original_title: Option[String],
  title: List[String],
  release_date: Option[LocalDate],
  external_ids: Option[List[String]])
