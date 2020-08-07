package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.ItemsScroller
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.{NonMatchResult, ScrapeCatalogType}
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import javax.inject.Inject
import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Success

class NetflixCatalogDeltaIngestJob @Inject()(
  deps: IngestDeltaJobDependencies
)(implicit executionContext: ExecutionContext)
    extends IngestDeltaJob[NetflixScrapedCatalogItem](deps)
    with SubscriptionNetworkDeltaAvailability[NetflixScrapedCatalogItem] {

  override protected def offerType: OfferType = OfferType.Subscription

  override protected def scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.NetflixCatalog

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

class IngestNetflixCatalogDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory,
  httpClientFactory: HttpClient.Factory,
  @SingleThreaded scheduledExecutor: ScheduledExecutorService
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[NetflixScrapedCatalogItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      NetflixScrapedCatalogItem,
      LiveIngestDeltaJobArgs
    ] {
  override protected val crawlerName: CrawlerName = CrawlStore.NetflixCatalog

  final private val netflixClient =
    httpClientFactory.create("www.netflix.com", HttpClientOptions.withTls)

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: NetflixScrapedCatalogItem,
    esItem: EsItem
  ): Option[ItemChange] = None

  override protected val supportedNetworks: Set[SupportedNetwork] = Set(
    SupportedNetwork.Netflix
  )

  override protected val externalSource: ExternalSource = ExternalSource.Netflix

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.NetflixCatalog

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: NetflixScrapedCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] =
    createAvailabilities(networks, item, scrapedItem).toList

  override protected def processRemovals(
    removals: List[PendingAvailabilityRemove]
  ): Future[List[PendingAvailabilityRemove]] = {
    var count = 0L
    AsyncStream
      .fromSeq(removals)
      .delayedMapF(250 millis, scheduledExecutor)(removal => {
        removal.externalId match {
          case Some(value) =>
            netflixClient.get(s"/title/${value}").map(_.status).map {
              case 404 => Some(removal)
              case _   => None
            }

          case None => Future.successful(Some(removal))
        }
      })
      .withEffect(_ => {
        count += 1
        if (count % 100 == 0) {
          logger.info(s"Queried ${count} netflix items to check if removed.")
        }
      })
      .collect {
        case Some(removal) => removal
      }
      .toList
      .andThen {
        case Success(value) =>
          logger.info(
            s"Found ${value.size} Netflix items that were not crawled but were still available."
          )
      }
  }
}
