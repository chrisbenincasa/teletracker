package com.teletracker.tasks.scraper.disney

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatching,
  ElasticsearchLookup,
  MatchMode
}
import com.teletracker.tasks.scraper.model.DisneyPlusCatalogItem
import com.teletracker.tasks.scraper.{IngestJob, IngestJobArgs, IngestJobParser}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.io.File
import java.net.URI
import java.time.LocalDate

class IngestDisneyPlusCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor,
  elasticsearchLookup: ElasticsearchLookup,
  teletrackerConfig: TeletrackerConfig)
    extends IngestJob[DisneyPlusCatalogItem]
    with ElasticsearchFallbackMatching[DisneyPlusCatalogItem] {
  override protected def networkNames: Set[String] = Set("disney-plus")

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode =
    elasticsearchLookup

  override protected def outputLocation(
    args: IngestJobArgs,
    rawArgs: Args
  ): Option[URI] = {
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

  override protected def getAdditionalOutputFiles: Seq[(File, String)] =
    Seq(getElasticsearchFallbackMatchFile -> "fallback-matches.txt")

  override protected def sanitizeItem(
    item: DisneyPlusCatalogItem
  ): DisneyPlusCatalogItem = {
    if (item.title.endsWith(" - Series")) {
      item.copy(
        title = item.title.replaceAll(" - Series", "").trim,
        thingType = Some(ThingType.Show)
      )
    } else {
      item
    }
  }

  override protected def isAvailable(
    item: DisneyPlusCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }
}
