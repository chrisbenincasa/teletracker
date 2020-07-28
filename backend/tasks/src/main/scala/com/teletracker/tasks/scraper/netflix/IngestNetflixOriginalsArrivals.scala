package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.netflix.NetflixOriginalScrapeItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.{
  IngestJob,
  IngestJobParser,
  SubscriptionNetworkAvailability
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext

class IngestNetflixOriginalsArrivals @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends IngestJob[NetflixOriginalScrapeItem](networkCache)
    with SubscriptionNetworkAvailability[NetflixOriginalScrapeItem] {
  override protected val crawlerName: CrawlerName =
    CrawlStore.NetflixOriginalsArriving

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Netflix
  )

  override protected val externalSource: ExternalSource = ExternalSource.Netflix

  override protected val scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixOriginalsArriving

  private val farIntoTheFuture = LocalDate.now().plusYears(1)

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def shouldProcessItem(
    item: NetflixOriginalScrapeItem
  ): Boolean = {
    item.availableLocalDate.exists(_.isBefore(farIntoTheFuture))
  }
}
