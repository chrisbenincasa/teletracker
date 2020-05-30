package com.teletracker.common.model.scraping

import com.teletracker.common.elasticsearch.model.EsItem
import io.circe.Codec

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
