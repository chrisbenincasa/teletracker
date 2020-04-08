package com.teletracker.tasks.scraper.model

import com.teletracker.tasks.scraper.ScrapedItem
import com.teletracker.common.util.json.circe._
import io.circe.Codec

object PotentialInput {
  implicit def codec[T <: ScrapedItem](
    implicit tCodec: Codec[T]
  ): Codec.AsObject[PotentialInput[T]] =
    io.circe.generic.semiauto.deriveCodec[PotentialInput[T]]
}

case class PotentialInput[T <: ScrapedItem: Codec](
  potential: PartialEsItem,
  scraped: T)
    extends ScrapedItem {
  override def availableDate: Option[String] = scraped.availableDate
  override def title: String = scraped.title
  override def releaseYear: Option[Int] = scraped.releaseYear
  override def category(): Option[String] = scraped.category
  override def network: String = scraped.network
  override def status: String = scraped.status
  override def externalId: Option[String] = scraped.externalId
  override def isMovie: Boolean = scraped.isMovie
  override def isTvShow: Boolean = scraped.isTvShow
  override def description: Option[String] = scraped.description
}
