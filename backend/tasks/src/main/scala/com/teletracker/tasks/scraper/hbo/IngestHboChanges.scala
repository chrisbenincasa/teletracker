package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{
  ItemLookup,
  ItemUpdater,
  ItemsScroller
}
import com.teletracker.common.model.scraping.{
  ScrapeItemType,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.loaders.CrawlAvailabilityItemLoaderFactory
import com.teletracker.tasks.scraper.{
  BaseSubscriptionNetworkAvailability,
  IngestDeltaJobDependencies,
  IngestJob,
  IngestJobApp,
  LiveIngestDeltaJob,
  LiveIngestDeltaJobArgs,
  SubscriptionNetworkAvailability
}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, LocalDate, LocalDateTime, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext

object IngestHboChanges extends IngestJobApp[IngestHboChanges]

class IngestHboChanges @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends IngestJob[HboScrapeChangesItem]
    with SubscriptionNetworkAvailability[HboScrapeChangesItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboChanges

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.HboGo, ExternalSource.HboNow)

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Eastern").getRules.getOffset(Instant.now())
}

class IngestHboChangesDelta @Inject()(
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext)
    extends LiveIngestDeltaJob[HboScrapeChangesItem](
      deps,
      itemsScroller,
      crawlAvailabilityItemLoaderFactory
    )
    with BaseSubscriptionNetworkAvailability[
      HboScrapeChangesItem,
      LiveIngestDeltaJobArgs
    ] {

  override def preparseArgs(args: RawArgs): LiveIngestDeltaJobArgs = {
    super.preparseArgs(args).copy(additionsOnly = true)
  }

  override protected val crawlerName: CrawlerName = CrawlStore.HboChanges

  override protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: HboScrapeChangesItem,
    esItem: EsItem
  ): Option[ItemChange] = {
    if (existing.start_date.isEmpty && incoming.start_date.isDefined) {
      None
    } else if (existing.end_date.isEmpty && incoming.end_date.isDefined) {
      Some(ItemChange(esItem, scrapedItem, ItemChangeUpdate))
    } else {
      None
    }
  }

  override protected val offerType: OfferType = OfferType.Subscription

  override protected val supportedNetworks: Set[SupportedNetwork] =
    Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)

  override protected val externalSource: ExternalSource = ExternalSource.HboGo

  override protected val scrapeItemType: ScrapeItemType =
    ScrapeItemType.HboChanges

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: HboScrapeChangesItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }
}

object HboScrapeChangesItem {
  implicit final val availabilityDetails
    : ScrapedItemAvailabilityDetails[HboScrapeChangesItem] =
    new ScrapedItemAvailabilityDetails[HboScrapeChangesItem] {
      override def offerType(t: HboScrapeChangesItem): OfferType =
        OfferType.Subscription

      override def uniqueKey(t: HboScrapeChangesItem): Option[String] =
        t.externalId

      override def externalIds(
        t: HboScrapeChangesItem
      ): Map[ExternalSource, String] =
        Map(
          ExternalSource.HboGo -> t.externalId,
          ExternalSource.HboMax -> t.externalId
        ).collect {
          case (source, Some(str)) => source -> str
        }
    }
}

@JsonCodec
case class HboScrapeChangesItem(
  availableDate: Option[String],
  title: String,
  parsedReleaseYear: Option[Int],
  category: Option[String],
  status: String,
  externalId: Option[String],
  itemType: ItemType)
    extends ScrapedItem {

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDateTime.parse(_)).map(_.toLocalDate)

  override def isMovie: Boolean =
    itemType == ItemType.Movie

  override def isTvShow: Boolean = itemType == ItemType.Show

  override def releaseYear: Option[Int] = parsedReleaseYear

  override def description: Option[String] = None

  override def network: String = "hbo"
}
