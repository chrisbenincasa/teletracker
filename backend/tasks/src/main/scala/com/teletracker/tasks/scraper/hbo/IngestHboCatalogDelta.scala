package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.ItemsScroller
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJob,
  IngestDeltaJobArgs,
  IngestDeltaJobDependencies,
  LiveIngestDeltaJob,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject

class IngestHboCatalogDelta @Inject()(deps: IngestDeltaJobDependencies)
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

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboCatalog
}

class IngestHboLiveCatalogDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller)
    extends LiveIngestDeltaJob[HboScrapedCatalogItem](deps, itemsScroller)
    with BaseSubscriptionNetworkAvailability[
      HboScrapedCatalogItem,
      IngestDeltaJobArgs
    ] {
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

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboCatalog

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
