package com.teletracker.common.pubsub

import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.model.scraping.apple.AppleTvItem
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.model.scraping.google.GooglePlayStoreItem
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.showtime.ShowtimeScrapeCatalogItem
import io.circe.generic.JsonCodec
import io.circe.{Codec, Decoder, Encoder, Json}
import scala.util.{Failure, Try}

object ScrapeItemIngestMessage {
  final private val HboCatalogItemType = "HboItem"
  final private val ShowtimeCatalogItemType = "ShowtimeItem"
  final private val NetflixCatalogItemType = "NetflixItem"
  final private val HuluCatalogItemType = "HuluItem"
  final private val DisneyPlusCatalogItemType = "DisneyPlusCatalogItem"
  final private val AmazonItemType = "AmazonItem"
  final private val AppleTvItemType = "AppleTv"
  final private val GooglePlayStoreItemType = "GooglePlayStoreItem"
}

@JsonCodec
case class ScrapeItemIngestMessage(
  `type`: String,
  version: Long,
  item: Json)
    extends EventBase {

  import ScrapeItemIngestMessage._

  def deserToScrapedItem: Try[ScrapedItem] = {
    `type` match {
      case HboCatalogItemType        => item.as[HboScrapedCatalogItem].toTry
      case NetflixCatalogItemType    => item.as[NetflixScrapedCatalogItem].toTry
      case HuluCatalogItemType       => item.as[HuluScrapeCatalogItem].toTry
      case DisneyPlusCatalogItemType => item.as[DisneyPlusCatalogItem].toTry
      case AmazonItemType            => item.as[AmazonItem].toTry
      case AppleTvItemType           => item.as[AppleTvItem].toTry
      case GooglePlayStoreItemType   => item.as[GooglePlayStoreItem].toTry
      case ShowtimeCatalogItemType   => item.as[ShowtimeScrapeCatalogItem].toTry
      case _ =>
        Failure(
          new IllegalArgumentException(s"Type ${`type`} is not recognized")
        )
    }
  }

  def deserItem[T: Decoder]: Try[T] = {
    item.as[T].toTry
  }
}

case class SpecificScrapeItemIngestMessage[T <: ScrapedItem](
  `type`: String,
  version: java.lang.Long,
  item: T)
    extends EventBase

object SpecificScrapeItemIngestMessage {
  implicit def codec[
    T <: ScrapedItem: Codec
  ]: Codec[SpecificScrapeItemIngestMessage[T]] =
    io.circe.generic.semiauto.deriveCodec
}
