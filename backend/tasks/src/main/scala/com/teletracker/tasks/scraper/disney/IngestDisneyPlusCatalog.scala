package com.teletracker.tasks.scraper.disney

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.ScrapeItemType
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.ElasticsearchFallbackMatching
import com.teletracker.tasks.scraper.{IngestJob, IngestJobParser}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate

class IngestDisneyPlusCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater)
    extends IngestJob[DisneyPlusCatalogItem] {
  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.DisneyPlusCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.DisneyPlus)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def outputLocation: Option[URI] = {
    if (rawArgs.valueOrDefault("outputLocal", true)) {
      Some(URI.create(s"file://${System.getProperty("user.dir")}"))
    } else {
      Some(
        URI.create(
          s"s3://${teletrackerConfig.data.s3_bucket}/ingest-results/disney-plus/catalog/$now"
        )
      )
    }
  }

//  override protected def sanitizeItem(
//    item: DisneyPlusCatalogItem
//  ): DisneyPlusCatalogItem = {
//    Seq(sanitizeSeriesTitle(_), fixItemType(_)).reduce(_.andThen(_)).apply(item)
//  }
//
//  private def sanitizeSeriesTitle(
//    item: DisneyPlusCatalogItem
//  ): DisneyPlusCatalogItem = {
//    if (item.title.endsWith(" - Series")) {
//      item.copy(
//        name = item.title.replaceAll(" - Series", "").trim,
//        `type` = ItemType.Show
//      )
//    } else {
//      item
//    }
//  }
//
//  private def fixItemType(
//    item: DisneyPlusCatalogItem
//  ): DisneyPlusCatalogItem = {
//    item.url match {
//      case Some(value) if value.contains("series") && item.isMovie =>
//        item.copy(`type` = ItemType.Show)
//      case _ => item
//    }
//  }

  override protected def isAvailable(
    item: DisneyPlusCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }
}
