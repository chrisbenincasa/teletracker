package com.teletracker.tasks.scraper.disney

import com.teletracker.common.db.dynamo.CrawlerName
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeCatalogType
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
import scala.concurrent.ExecutionContext

class IngestDisneyPlusPotentialMatches @Inject()(
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends IngestPotentialMatches[DisneyPlusCatalogItem](networkCache)
    with SubscriptionNetworkAvailability[PotentialInput[DisneyPlusCatalogItem]] {
  override protected def crawlerName: CrawlerName = ???

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.DisneyPlus
  )

  override protected val externalSource: ExternalSource =
    ExternalSource.DisneyPlus

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.DisneyPlusCatalog
}
