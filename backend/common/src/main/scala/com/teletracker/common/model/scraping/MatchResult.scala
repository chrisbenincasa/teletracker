package com.teletracker.common.model.scraping

import com.teletracker.common.elasticsearch.model.EsItem
import io.circe.Codec
import io.circe.generic.JsonCodec

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
