package com.teletracker.common.pubsub

import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Json}
import scala.util.{Failure, Try}

object ScrapeItemIngestMessage {
  final private val HboCatalogItemType = "HboItem"
  final private val ShowtimeCatalogItem = "ShowtimeItem"
  final private val NetflixCatalogItemType = "NetflixItem"
  final private val HuluCatalogItemType = "HuluItem"
  final private val DisneyPlusCatalogItemType = "DisneyPlusCatalogItem"
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
