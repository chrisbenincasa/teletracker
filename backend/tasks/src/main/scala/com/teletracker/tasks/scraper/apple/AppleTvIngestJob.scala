package com.teletracker.tasks.scraper.apple

import com.teletracker.common.availability.NetworkAvailability
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.model.scraping.apple.AppleTvItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJob
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext

class AppleTvIngestJob @Inject()(
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends IngestJob[AppleTvItem](networkCache) {
  override protected val crawlerName: CrawlerName = CrawlStore.AppleTvCatalog

  override protected val externalSource: ExternalSource =
    ExternalSource.AppleTv

  override protected val scrapeItemType: ScrapeCatalogType =
    ScrapeCatalogType.AppleTvCatalog

  override protected val supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.AppleTv)

  override protected def shouldProcessItem(item: AppleTvItem): Boolean = {
    item.isMovie
  }

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapeItem: AppleTvItem
  ): Seq[EsAvailability] = {
    val networkMap = networks.flatMap(n => n.supportedNetwork.map(_ -> n)).toMap
    val appleTvNetwork = networkMap(SupportedNetwork.AppleTv)

    scrapeItem.offers
      .getOrElse(Nil)
      .map(offer => {
        EsAvailability(
          network_id = appleTvNetwork.id,
          network_name = Some(appleTvNetwork.slug.value.toLowerCase()),
          region = "US",
          start_date = None,
          end_date = None,
          offer_type = offer.offerType.getName.toLowerCase,
          cost = offer.price,
          currency = offer.currency,
          presentation_type = offer.quality.map(_.getName.toLowerCase),
          links = None,
          num_seasons_available = None,
          last_updated = Some(now),
          last_updated_by = Some(getClass.getSimpleName),
          crawler = getContext.flatMap(_.crawlerInfo).map(_.crawler.name),
          crawl_version = getContext.flatMap(_.crawlerInfo).flatMap(_.version)
        )
      })
  }
}
