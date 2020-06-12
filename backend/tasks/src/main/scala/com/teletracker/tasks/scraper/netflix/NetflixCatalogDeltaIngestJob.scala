package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeItemType}
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import javax.inject.Inject
import scala.concurrent.Future

class NetflixCatalogDeltaIngestJob @Inject()(deps: IngestDeltaJobDependencies)
    extends IngestDeltaJob[NetflixScrapedCatalogItem](deps)
    with SubscriptionNetworkDeltaAvailability[NetflixScrapedCatalogItem] {

  override protected def offerType: OfferType = OfferType.Subscription

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Netflix
  )
  override protected val externalSource: ExternalSource = ExternalSource.Netflix

  override protected def findPotentialMatches(
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

  override protected def uniqueKeyForIncoming(
    item: NetflixScrapedCatalogItem
  ): Option[String] =
    item.externalId

  override protected def externalIds(
    item: NetflixScrapedCatalogItem
  ): Map[ExternalSource, String] =
    uniqueKeyForIncoming(item)
      .map(key => Map(ExternalSource.Netflix -> key))
      .getOrElse(Map.empty)
}
