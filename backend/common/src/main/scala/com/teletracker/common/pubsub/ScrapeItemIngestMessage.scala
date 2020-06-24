package com.teletracker.common.pubsub

import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.common.pubsub.ScrapeItemIngestMessage.HboCatalogItem
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Json}
import scala.util.{Failure, Try}

object ScrapeItemIngestMessage {
  sealed trait ScrapeItemType {
    def typ: String
  }
  case object HboCatalogItem extends ScrapeItemType {
    override val typ: String = "HboItem"
  }
}

@JsonCodec
case class ScrapeItemIngestMessage(
  `type`: String,
  version: Long,
  item: Json)
    extends EventBase {

  def deserToScrapedItem: Try[ScrapedItem] = {
    `type` match {
      case HboCatalogItem.typ => item.as[HboScrapedCatalogItem].toTry
      case _ =>
        Failure(
          new IllegalArgumentException(s"Type ${`type`} is not recognized")
        )
    }
  }

  def deserItem[T: Decoder]: Try[T] = {
    `type` match {
      case HboCatalogItem.typ => item.as[T].toTry
      case _ =>
        Failure(
          new IllegalArgumentException(s"Type ${`type`} is not recognized")
        )
    }
  }
}
