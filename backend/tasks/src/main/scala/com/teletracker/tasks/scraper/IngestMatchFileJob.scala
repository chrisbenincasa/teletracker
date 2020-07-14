package com.teletracker.tasks.scraper
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.model.scraping.{
  ScrapeItemType,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.matching.{DirectLookupMethod, LookupMethod}
import com.teletracker.tasks.scraper.model.MatchInput
import io.circe.Codec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.ExecutionContext

abstract class IngestMatchFileJob[
  T <: ScrapedItem: Codec: ScrapedItemAvailabilityDetails
](implicit executionContext: ExecutionContext)
    extends IngestJob[MatchInput[T]] {
  @Inject private[this] var directLookupMethod: DirectLookupMethod = _

  // Csst is safe because we know scrapedItem is not accessed

  override protected def lookupMethod(): LookupMethod[MatchInput[T]] =
    directLookupMethod.asInstanceOf[LookupMethod[MatchInput[T]]]
}

class IngestNetflixCatalogMatchFile @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater
)(implicit executionContext: ExecutionContext)
    extends IngestMatchFileJob[NetflixScrapedCatalogItem]
    with SubscriptionNetworkAvailability[MatchInput[NetflixScrapedCatalogItem]] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Netflix)
}
