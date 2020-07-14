package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.ItemsScroller
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJobDependencies,
  IngestJobParser,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HuluCatalogDeltaIngestJob @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[HuluScrapeCatalogItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      HuluScrapeCatalogItem,
      LiveIngestDeltaJobArgs
    ] {

  override protected def offerType: OfferType = OfferType.Subscription

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Hulu
  )
  override protected val externalSource: ExternalSource = ExternalSource.Hulu

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKeyForIncoming(
    item: HuluScrapeCatalogItem
  ): Option[String] =
    item.externalId

  override protected def externalIds(
    item: HuluScrapeCatalogItem
  ): Map[ExternalSource, String] = {
    uniqueKeyForIncoming(item)
      .map(key => Map(ExternalSource.Hulu -> key))
      .getOrElse(Map.empty)
  }

  override protected def processItemChange(
    before: EsItem,
    after: HuluScrapeCatalogItem
  ): Seq[ItemChange] = {
    after.additionalServiceRequired match {
      // Item is now exclusive to an addon
      case Some(_) => Seq(ItemChange(before, after, ItemChangeRemove))
      // Item is not exclusive to another additional service anymore
      case None => Seq(ItemChange(before, after, ItemChangeUpdate))
    }
  }

  override protected val crawlerName: CrawlerName = CrawlStore.HuluCatalog

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HuluScrapeCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = None

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HuluScrapeCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }
}
