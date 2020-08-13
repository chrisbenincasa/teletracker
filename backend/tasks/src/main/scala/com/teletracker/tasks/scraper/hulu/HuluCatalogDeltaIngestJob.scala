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
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJobDependencies,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs
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

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.HuluCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Hulu
  )

  override protected val externalSource: ExternalSource = ExternalSource.Hulu

  override protected val crawlerName: CrawlerName = CrawlStore.HuluCatalog

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HuluScrapeCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = {
    scrapedItem.additionalServiceRequired match {
      // Item is now exclusive to an addon
      case Some(_) => Some(ItemChange(esItem, scrapedItem, ItemChangeRemove))
      // Item is not exclusive to another additional service anymore
      case None => Some(ItemChange(esItem, scrapedItem, ItemChangeUpdate))
    }
  }

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HuluScrapeCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }

  override protected def shouldIncludeAfterItem(
    item: HuluScrapeCatalogItem
  ): Boolean = {
    val noAdditionalServiceRequired = item.additionalServiceRequired.isEmpty || item.additionalServiceRequired.get.isEmpty
    val isEnEspanol = item.title.toLowerCase.contains("en español")
    noAdditionalServiceRequired && !isEnEspanol
  }
}
