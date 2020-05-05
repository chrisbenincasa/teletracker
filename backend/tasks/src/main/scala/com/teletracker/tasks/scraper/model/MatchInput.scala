package com.teletracker.tasks.scraper.model

import com.teletracker.common.elasticsearch.EsItem
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.ScrapedItem
import io.circe.Codec

object MatchInput {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec[MatchInput[T]] =
    io.circe.generic.semiauto.deriveCodec[MatchInput[T]]
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
}
