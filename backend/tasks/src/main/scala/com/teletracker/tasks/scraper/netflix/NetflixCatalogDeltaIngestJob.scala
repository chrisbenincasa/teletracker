package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.availability.NetworkAvailability
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  PresentationType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.model.EsAvailability
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeItemType}
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  IngestDeltaJobArgs,
  IngestJobParser,
  SubscriptionNetworkAvailability,
  SubscriptionNetworkDeltaAvailability
}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.Future

case class NetflixCatalogDeltaIngestJob @Inject()()
    extends IngestDeltaJob[NetflixScrapedCatalogItem]
    with SubscriptionNetworkDeltaAvailability[NetflixScrapedCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Netflix
  )
  override protected val externalSource: ExternalSource = ExternalSource.Netflix

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
