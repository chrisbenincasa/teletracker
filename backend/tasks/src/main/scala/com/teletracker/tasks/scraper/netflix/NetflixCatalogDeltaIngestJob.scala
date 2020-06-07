package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  PresentationType
}
import com.teletracker.common.elasticsearch.model.EsAvailability
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeItemType}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.ElasticsearchLookup
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  IngestDeltaJobArgs,
  IngestJobParser
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID
import scala.concurrent.Future

case class NetflixCatalogDeltaIngestJob @Inject()(
  s3: S3Client,
  networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestDeltaJob[NetflixScrapedCatalogItem](elasticsearchLookup) {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixCatalog

  override protected val networkNames: Set[String] = Set("netflix")
  override protected val externalSource: ExternalSource = ExternalSource.Netflix

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    itemId: UUID,
    scrapedItem: NetflixScrapedCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    List(PresentationType.SD, PresentationType.HD).flatMap(presentationType => {
      networks.toList.map(network => {
        EsAvailability(
          network_id = network.id,
          network_name = Some(network.name),
          region = "US",
          start_date = None,
          end_date = None,
          offer_type = OfferType.Subscription.toString,
          cost = None,
          currency = None,
          presentation_type = Some(presentationType.getName),
          links = None,
          // TODO: NO
          num_seasons_available = None
        )
      })
    })
  }

  override protected def handleNonMatches(
    args: IngestDeltaJobArgs,
    nonMatches: List[NetflixScrapedCatalogItem]
  ): Future[List[NonMatchResult[NetflixScrapedCatalogItem]]] = {
    elasticsearchFallbackMatcher
      .create(getElasticsearchFallbackMatcherOptions)
      .handleNonMatches(
        args,
        nonMatches
      )
      .map(results => {
        results.map(result => {
          result.amendedScrapedItem.externalId match {
            case Some(value) =>
              result.copy(
                originalScrapedItem = nonMatches
                  .find(_.externalId.contains(value))
                  .getOrElse(result.amendedScrapedItem)
              )

            case None => result
          }
        })
      })
  }

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: NetflixScrapedCatalogItem): String =
    item.externalId.get

  override protected def externalIds(
    item: NetflixScrapedCatalogItem
  ): Map[ExternalSource, String] =
    Map(
      ExternalSource.Netflix -> uniqueKey(item)
    )
}
