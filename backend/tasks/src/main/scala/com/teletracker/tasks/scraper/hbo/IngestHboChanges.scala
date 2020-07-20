package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.model.{ExternalSource, ItemType, OfferType}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.{
  ScrapeItemType,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.{
  IngestJob,
  IngestJobApp,
  SubscriptionNetworkAvailability
}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time._
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
  override val releaseYear: Option[Int],
  category: Option[String],
  status: String,
  externalId: Option[String],
  itemType: ItemType)
    extends ScrapedItem {

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDateTime.parse(_)).map(_.toLocalDate)

  override def description: Option[String] = None

  override def network: String = "hbo"
}
