package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{
  ItemLookup,
  ItemUpdater,
  ItemsScroller
}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.scraping.hbo.HboMaxScrapedCatalogItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate
import java.util.regex.Pattern
import scala.concurrent.ExecutionContext

trait CommonHboMaxCatalogIngest {
  protected def scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.HboMaxCatalog

  protected def externalSources: List[ExternalSource] =
    List(ExternalSource.HboMax)
}

class IngestHboMaxCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends IngestJob[HboMaxScrapedCatalogItem](networkCache)
    with SubscriptionNetworkAvailability[HboMaxScrapedCatalogItem]
    with CommonHboMaxCatalogIngest {
  override protected val crawlerName: CrawlerName = CrawlStore.HboMaxCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.HboMax
  )

  override protected val externalSource: ExternalSource = ExternalSource.HboMax

  override protected def isAvailable(
    item: HboMaxScrapedCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  private val pattern =
    Pattern.compile("Director's Cut", Pattern.CASE_INSENSITIVE)

  override protected def sanitizeItem(
    item: HboMaxScrapedCatalogItem
  ): HboMaxScrapedCatalogItem = {
    item.copy(title = pattern.matcher(item.title).replaceAll("").trim)
  }

  override protected def itemUniqueIdentifier(
    item: HboMaxScrapedCatalogItem
  ): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }
}

class IngestHboMaxCatalogDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[HboMaxScrapedCatalogItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      HboMaxScrapedCatalogItem,
      LiveIngestDeltaJobArgs
    ] {
  override protected val crawlerName: CrawlerName = CrawlStore.HboMaxCatalog

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HboMaxScrapedCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = None

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.HboMax
  )

  override protected val externalSource: ExternalSource = ExternalSource.HboMax

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.HboMaxCatalog

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HboMaxScrapedCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] =
    createAvailabilities(networks, item, scrapedItem).toList
}
