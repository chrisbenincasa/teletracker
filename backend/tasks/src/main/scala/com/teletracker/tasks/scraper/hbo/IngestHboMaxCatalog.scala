package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{EsExternalId, EsItem}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeItemType}
import com.teletracker.common.model.scraping.hbo.HboMaxCatalogItem
import com.teletracker.common.util.{AsyncStream, NetworkCache}
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.matching.ElasticsearchFallbackMatching
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate
import java.util.UUID
import java.util.regex.Pattern
import scala.concurrent.Future

class IngestHboMaxCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  externalIdMappingStore: ElasticsearchExternalIdMappingStore)
    extends IngestJob[HboMaxCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboMaxCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.HboMax)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def isAvailable(
    item: HboMaxCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  private val pattern =
    Pattern.compile("Director's Cut", Pattern.CASE_INSENSITIVE)

  override protected def sanitizeItem(
    item: HboMaxCatalogItem
  ): HboMaxCatalogItem = {
    item.copy(title = pattern.matcher(item.title).replaceAll("").trim)
  }

  override protected def itemUniqueIdentifier(
    item: HboMaxCatalogItem
  ): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }

  override protected def getExternalIds(
    item: HboMaxCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.HboMax -> item.externalId
    ).collect {
      case (k, Some(v)) => k -> v
    }
  }

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[HboMaxCatalogItem]
  ): Future[List[NonMatchResult[HboMaxCatalogItem]]] = {
    val nonMatchesById = nonMatches.map(m => m.id -> m).toMap

    val externalsToSearch = nonMatches
      .filter(_.couldBeOnHboGo.contains(true))
      .filter(_.externalId.isDefined)
      .map(item => {
        EsExternalId(ExternalSource.HboGo, item.id) -> item.itemType
      })

    externalIdMappingStore
      .getItemIdsForExternalIds(externalsToSearch.toSet)
      .flatMap(foundMatches => {
        AsyncStream
          .fromSeq(foundMatches.values.toSet.grouped(10).toSeq)
          .mapF(group => {
            itemLookup
              .lookupItemsByIds(group)
              .map(_.collect {
                case (uuid, Some(item)) => uuid -> item
              })
          })
          .foldLeft(Map.empty[UUID, EsItem])(_ ++ _)
          .flatMap(foundItems => {
            val potentialMatches = for {
              (id, scrapedItem) <- nonMatchesById
              esItemId <- foundMatches
                .get(
                  EsExternalId(ExternalSource.HboGo, id),
                  scrapedItem.itemType
                )
                .toList
              esItem <- foundItems.get(esItemId).toList
            } yield {
              NonMatchResult(scrapedItem, scrapedItem, esItem)
            }

            writePotentialMatches(
              potentialMatches.map(m => m.esItem -> m.originalScrapedItem)
            )

            val foundIds = potentialMatches.map(_.originalScrapedItem.id)

            val stillMissingIds = nonMatchesById.keySet -- foundIds

            super.handleNonMatches(
              args,
              stillMissingIds.flatMap(nonMatchesById.get).toList
            )
          })
      })
  }
}
