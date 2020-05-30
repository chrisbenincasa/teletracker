package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  PresentationType
}
import com.teletracker.common.elasticsearch.model.EsAvailability
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.ElasticsearchLookup
import com.teletracker.tasks.scraper.{IngestDeltaJob, IngestJobParser}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

class HuluCatalogDeltaIngestJob @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestDeltaJob[HuluScrapeCatalogItem](elasticsearchLookup) {
  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

  override protected val networkNames: Set[String] = Set("hulu")
  override protected val externalSource: ExternalSource = ExternalSource.Hulu

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    itemId: UUID,
    scrapedItem: HuluScrapeCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    List(PresentationType.SD, PresentationType.HD).flatMap(presentationType => {
      networks.toList.map(network => {
        EsAvailability(
          network_id = network.id,
          network_name = Some(network.name),
          region = "US",
          start_date = None,
          end_date = None,
          offer_type = OfferType.Subscription.toString,
          cost = None,
          currency = None,
          presentation_type = Some(presentationType.getName),
          None,
          num_seasons_available = None
        )
      })
    })
  }

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: HuluScrapeCatalogItem): String =
    item.externalId.get

  override protected def externalIds(
    item: HuluScrapeCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.Hulu -> uniqueKey(item)
    )
  }

  override protected def processItemChange(
    before: HuluScrapeCatalogItem,
    after: HuluScrapeCatalogItem
  ): Option[ItemChange] = {
    val changeType = (
      before.additionalServiceRequired,
      after.additionalServiceRequired
    ) match {
      // Item is not exclusive to another additional service anymore
      case (Some(_), None) => Some(ItemChangeUpdate)
      // Item is now exclusive to an addon
      case (None, Some(_)) => Some(ItemChangeRemove)
      case _               => None // TODO: Handle other change types
    }

    changeType.map(ItemChange(before, after, _))
  }
}
