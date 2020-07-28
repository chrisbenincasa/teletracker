package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.debug.{
  GenerateMatchCsv,
  GeneratePotentialMatchCsv
}
import com.teletracker.tasks.scraper.{
  IngestJob,
  IngestJobParser,
  SubscriptionNetworkAvailability
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import scala.concurrent.ExecutionContext

class IngestHuluCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends IngestJob[HuluScrapeCatalogItem](networkCache)
    with SubscriptionNetworkAvailability[HuluScrapeCatalogItem] {
  override protected val crawlerName: CrawlerName = CrawlStore.HuluCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Hulu
  )

  override protected val externalSource: ExternalSource = ExternalSource.Hulu

  override protected val scrapeItemType: ScrapeItemType =
    ScrapeItemType.HuluCatalog

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
  ): List[TeletrackerTaskQueueMessage] = {
    List(
      TeletrackerTask.taskMessage[GenerateMatchCsv](
        Map(
          "input" -> URI
            .create(s"file://${matchItemsFile.getAbsolutePath}")
            .toString,
          "type" -> ScrapeItemType.HuluCatalog.toString
        )
      ),
      TeletrackerTask.taskMessage[GeneratePotentialMatchCsv](
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
