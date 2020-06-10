package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  IngestDeltaJobDependencies,
  IngestJobParser,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject

class HuluCatalogDeltaIngestJob @Inject()(deps: IngestDeltaJobDependencies)
    extends IngestDeltaJob[HuluScrapeCatalogItem](deps)
    with SubscriptionNetworkDeltaAvailability[HuluScrapeCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Hulu
  )
  override protected val externalSource: ExternalSource = ExternalSource.Hulu

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
