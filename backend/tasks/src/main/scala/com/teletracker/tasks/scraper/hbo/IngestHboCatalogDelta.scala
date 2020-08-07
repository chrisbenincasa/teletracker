package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.ItemsScroller
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJob,
  IngestDeltaJobArgs,
  IngestDeltaJobDependencies,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IngestHboCatalogDelta @Inject()(
  deps: IngestDeltaJobDependencies
)(implicit executionContext: ExecutionContext)
    extends IngestDeltaJob[HboScrapedCatalogItem](deps)
    with SubscriptionNetworkDeltaAvailability[HboScrapedCatalogItem] {
  override protected def offerType: OfferType = OfferType.Subscription

  override protected def supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)

  override protected def externalSource: ExternalSource = ExternalSource.HboGo

  override protected def uniqueKeyForIncoming(
    item: HboScrapedCatalogItem
  ): Option[String] =
    item.externalId

  override protected def externalIds(
    item: HboScrapedCatalogItem
  ): Map[ExternalSource, String] =
    Map(
      ExternalSource.HboGo -> item.externalId.get,
      ExternalSource.HboMax -> item.externalId.get
    )

  override protected def scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.HboCatalog
}

class IngestHboLiveCatalogDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[HboScrapedCatalogItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      HboScrapedCatalogItem,
      LiveIngestDeltaJobArgs
    ] {
  override protected val crawlerName: CrawlerName = CrawlStore.HboCatalog

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)

  override protected val externalSource: ExternalSource = ExternalSource.HboGo

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.HboCatalog

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HboScrapedCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HboScrapedCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = {
    None
  }
}
