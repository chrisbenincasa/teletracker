package com.teletracker.tasks.scraper.hbo

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
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.matching.LookupMethod
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJobDependencies,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs
}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IngestHboChangesDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[HboScrapeChangesItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      HboScrapeChangesItem,
      LiveIngestDeltaJobArgs
    ] {

  // We don't have external IDs for HBO upcoming changes, so only match on title
  override protected val lookupMethod: LookupMethod[HboScrapeChangesItem] = {
    deps.elasticsearchExactTitleLookup.create
  }

  // No external IDs either, so we just say the title is unique
  override protected def uniqueKeyForIncoming(
    item: HboScrapeChangesItem
  ): Option[String] = {
    Some(item.title)
  }

  override def preparseArgs(args: RawArgs): LiveIngestDeltaJobArgs = {
    super.preparseArgs(args).copy(additionsOnly = true)
  }

  override protected val crawlerName: CrawlerName = CrawlStore.HboChanges

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HboScrapeChangesItem,
    esItem: EsItem
  ): Option[ItemChange] = {
    if (existing.start_date.isEmpty && incoming.start_date.isDefined) {
      None
    } else if (existing.end_date.isEmpty && incoming.end_date.isDefined) {
      Some(ItemChange(esItem, scrapedItem, ItemChangeUpdate))
    } else {
      None
    }
  }

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)

  override protected val externalSource: ExternalSource = ExternalSource.HboGo

  override protected val scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboChanges

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HboScrapeChangesItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }
}
