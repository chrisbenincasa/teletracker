package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject

class IngestHboCatalogDelta @Inject()()
    extends IngestDeltaJob[HboScrapedCatalogItem]
    with SubscriptionNetworkDeltaAvailability[HboScrapedCatalogItem] {
  override protected def supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)

  override protected def externalSource: ExternalSource = ExternalSource.HboGo

  override protected def uniqueKey(item: HboScrapedCatalogItem): String =
    item.externalId.getOrElse(item.title)

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
