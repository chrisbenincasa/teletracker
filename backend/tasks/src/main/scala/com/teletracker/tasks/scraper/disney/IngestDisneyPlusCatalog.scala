package com.teletracker.tasks.scraper.disney

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
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJobDependencies,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs
}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IngestDisneyPlusCatalog @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[DisneyPlusCatalogItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      DisneyPlusCatalogItem,
      LiveIngestDeltaJobArgs
    ] {
  override protected val scrapeItemType: ScrapeItemType =
    ScrapeItemType.DisneyPlusCatalog

  override protected val crawlerName: CrawlerName = CrawlStore.DisneyPlusCatalog

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: DisneyPlusCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = None

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.DisneyPlus
  )

  override protected val externalSource: ExternalSource =
    ExternalSource.DisneyPlus

  override protected val offerType: OfferType = OfferType.Subscription

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: DisneyPlusCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] =
    createAvailabilities(networks, item, scrapedItem).toList
}
