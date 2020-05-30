package com.teletracker.common.model.scraping

import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.model.scraping
import io.circe.Codec

case class NonMatchResult[T <: ScrapedItem](
  amendedScrapedItem: T,
  originalScrapedItem: T,
  esItem: EsItem) {
  def toMatchResult: MatchResult[T] =
    scraping.MatchResult(amendedScrapedItem, esItem)

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
