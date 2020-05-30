package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.debug.{
  GenerateMatchCsv,
  GeneratePotentialMatchCsv
}
import com.teletracker.tasks.scraper.matching.ElasticsearchFallbackMatching
import com.teletracker.tasks.scraper.{IngestJob, IngestJobArgs, IngestJobParser}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate

class IngestHuluCatalog @Inject()(
  protected val teletrackerConfig: TeletrackerConfig,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HuluScrapeCatalogItem]
    with ElasticsearchFallbackMatching[HuluScrapeCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Hulu)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def shouldProcessItem(
    item: HuluScrapeCatalogItem
  ): Boolean = {
    !item.title.toLowerCase().contains("en español") &&
    !item.title.toLowerCase().contains("en espanol") &&
    item.additionalServiceRequired.isEmpty
  }

  override protected def sanitizeItem(
    item: HuluScrapeCatalogItem
  ): HuluScrapeCatalogItem =
    if (item.releaseYear.isDefined && item.title.endsWith(
          s"(${item.releaseYear.get})"
        )) {
      item.copy(
        title =
          item.title.replaceAllLiterally(s"(${item.releaseYear.get})", "").trim
      )
    } else {
      item
    }

  override protected def isAvailable(
    item: HuluScrapeCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  override protected def itemUniqueIdentifier(
    item: HuluScrapeCatalogItem
  ): String =
    item.externalId.get

  override protected def getExternalIds(
    item: HuluScrapeCatalogItem
  ): Map[ExternalSource, String] =
    item.externalId
      .map(id => Map(ExternalSource.Hulu -> id))
      .getOrElse(Map.empty)

  override protected def followupTasksToSchedule(
    args: IngestJobArgs,
    rawArgs: Args
  ): List[TeletrackerTaskQueueMessage] = {
    List(
      TaskMessageHelper.forTask[GenerateMatchCsv](
        Map(
          "input" -> URI
            .create(s"file://${matchItemsFile.getAbsolutePath}")
            .toString,
          "type" -> ScrapeItemType.HuluCatalog.toString
        )
      ),
      TaskMessageHelper.forTask[GeneratePotentialMatchCsv](
        Map(
          "input" -> URI
            .create(s"file://${potentialMatchFile.getAbsolutePath}")
            .toString,
          "type" -> ScrapeItemType.HuluCatalog.toString
        )
      )
    )
  }
}
