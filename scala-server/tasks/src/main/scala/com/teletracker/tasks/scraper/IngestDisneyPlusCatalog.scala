package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import com.teletracker.tasks.scraper.model.DisneyPlusCatalogItem
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate

class IngestDisneyPlusCatalog @Inject()(
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[DisneyPlusCatalogItem]
    with IngestJobWithElasticsearch[DisneyPlusCatalogItem]
    with ElasticsearchFallbackMatcher[DisneyPlusCatalogItem] {
  override protected def networkNames: Set[String] = Set("disney-plus")

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode =
    elasticsearchLookup

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
