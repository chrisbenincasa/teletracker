package com.teletracker.tasks.scraper.amazon

import com.teletracker.common.availability.NetworkAvailability
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJob
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class AmazonIngestJob @Inject()(
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends IngestJob[AmazonItem](networkCache) {
  override protected val crawlerName: CrawlerName = CrawlStore.AmazonCatalog

  override protected val externalSource: ExternalSource =
    ExternalSource.AmazonVideo

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.AmazonVideo

  override protected val supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.AmazonPrimeVideo, SupportedNetwork.AmazonVideo)

  override protected def shouldProcessItem(item: AmazonItem): Boolean = {
    item.isMovie
  }

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapeItem: AmazonItem
  ): Seq[EsAvailability] = {
    val networkMap = networks.flatMap(n => n.supportedNetwork.map(_ -> n)).toMap
    val primeNetwork = networkMap(SupportedNetwork.AmazonPrimeVideo)
    val regularNetwork = networkMap(SupportedNetwork.AmazonVideo)
    val hasPrime = scrapeItem.availableOnPrime

    val paidOffers = scrapeItem.offers
      .getOrElse(Nil)
      .map(offer => {
        EsAvailability(
          network_id = regularNetwork.id,
          network_name = Some(regularNetwork.slug.value.toLowerCase()),
          region = "US",
          start_date = None,
          end_date = None,
          offer_type = offer.offerType.toString,
          cost = offer.price,
          currency = offer.currency,
          presentation_type = offer.quality.map(_.toString),
          links = None,
          num_seasons_available = None,
          last_updated = Some(OffsetDateTime.now()),
          last_updated_by = Some(getClass.getSimpleName),
          crawler = getContext.flatMap(_.crawlerInfo).map(_.crawler.name),
          crawl_version = getContext.flatMap(_.crawlerInfo).flatMap(_.version)
        )
      })

    val primeAvailability = if (hasPrime) {
      NetworkAvailability.forSupportedNetwork(
        supportedNetwork = SupportedNetwork.AmazonPrimeVideo,
        storedNetwork = primeNetwork,
        updateSource = Some(getClass.getSimpleName),
        crawlerInfo = getContext.flatMap(_.crawlerInfo)
      )
    } else {
      Nil
    }

    paidOffers ++ primeAvailability
  }
}
