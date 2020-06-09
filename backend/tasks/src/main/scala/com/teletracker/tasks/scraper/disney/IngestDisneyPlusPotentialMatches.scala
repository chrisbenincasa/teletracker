package com.teletracker.tasks.scraper.disney

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.model.PotentialInput
import com.teletracker.tasks.scraper.{
  IngestJobParser,
  IngestPotentialMatches,
  SubscriptionNetworkAvailability
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client

class IngestDisneyPlusPotentialMatches @Inject()(
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache)
    extends IngestPotentialMatches[DisneyPlusCatalogItem]
    with SubscriptionNetworkAvailability[PotentialInput[DisneyPlusCatalogItem]] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.DisneyPlusCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.DisneyPlus)

  override protected def parseMode: IngestJobParser.ParseMode =
    IngestJobParser.JsonPerLine
}
