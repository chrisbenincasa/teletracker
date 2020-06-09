package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hbo.HboScrapedCatalogItem
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.debug.{
  GenerateMatchCsv,
  GeneratePotentialMatchCsv
}
import com.teletracker.tasks.scraper.matching.ElasticsearchFallbackMatching
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import java.util.regex.Pattern

class IngestHboCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater)
    extends IngestJob[HboScrapedCatalogItem]
    with SubscriptionNetworkAvailability[HboScrapedCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.HboGo, ExternalSource.HboMax)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def isAvailable(
    item: HboScrapedCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  override protected def shouldProcessItem(
    item: HboScrapedCatalogItem
  ): Boolean = {
    !item.title.toLowerCase.contains("HBO First Look")
  }

  private val pattern =
    Pattern.compile("Director's Cut", Pattern.CASE_INSENSITIVE)

  override protected def sanitizeItem(
    item: HboScrapedCatalogItem
  ): HboScrapedCatalogItem = {
    item.copy(title = pattern.matcher(item.title).replaceAll("").trim)
  }

  override protected def itemUniqueIdentifier(
    item: HboScrapedCatalogItem
  ): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }

  override protected def getExternalIds(
    item: HboScrapedCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.HboGo -> item.externalId,
      ExternalSource.HboMax -> item.externalId
    ).collect {
      case (k, Some(v)) => k -> v
    }
  }

  override protected def followupTasksToSchedule(
  ): List[TeletrackerTaskQueueMessage] = {
    List(
      TeletrackerTask.taskMessage[GenerateMatchCsv](
        Map(
          "input" -> URI
            .create(s"file://${matchItemsFile.getAbsolutePath}")
            .toString,
          "type" -> ScrapeItemType.HboCatalog.toString
        )
      ),
      TeletrackerTask.taskMessage[GeneratePotentialMatchCsv](
        Map(
          "input" -> URI
            .create(s"file://${potentialMatchFile.getAbsolutePath}")
            .toString,
          "type" -> ScrapeItemType.HboCatalog.toString
        )
      )
    )
  }
}
