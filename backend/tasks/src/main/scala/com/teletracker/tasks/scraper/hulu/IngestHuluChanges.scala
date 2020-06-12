package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeItemType}
import com.teletracker.common.model.scraping.hulu.HuluScrapeItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, ZoneId, ZoneOffset}
import scala.concurrent.Future

class IngestHuluChanges @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  huluFallbackMatching: HuluFallbackMatching)
    extends IngestJob[HuluScrapeItem]
    with SubscriptionNetworkAvailability[HuluScrapeItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

  private val premiumNetworks = Set("hbo", "starz", "showtime")

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Hulu)

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def findPotentialMatches(
    args: IngestJobArgs,
    nonMatches: List[HuluScrapeItem]
  ): Future[List[NonMatchResult[HuluScrapeItem]]] = {
    huluFallbackMatching.handleNonMatches(args, nonMatches)
  }

  override protected def shouldProcessItem(item: HuluScrapeItem): Boolean = {
    val category = item.category.getOrElse("").toLowerCase()
    val shouldInclude = if (premiumNetworks.exists(category.contains)) {
      if (category.contains("series")) {
        !item.notes
          .toLowerCase()
          .contains("premiere")
      } else {
        false
      }
    } else {
      true
    }

    shouldInclude
  }

  override protected def sanitizeItem(item: HuluScrapeItem): HuluScrapeItem =
    item.copy(
      title = HuluSanitization.sanitizeTitle(item.title)
    )
}
