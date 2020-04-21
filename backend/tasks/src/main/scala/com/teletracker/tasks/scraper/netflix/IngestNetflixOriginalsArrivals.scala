package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.{IngestJob, IngestJobParser, ScrapedItem}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}

class IngestNetflixOriginalsArrivals @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[NetflixOriginalScrapeItem] {

  private val farIntoTheFuture = LocalDate.now().plusYears(1)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def lookupMethod: LookupMethod[NetflixOriginalScrapeItem] =
    elasticsearchLookup.toMethod[NetflixOriginalScrapeItem]

  override protected def networkNames: Set[String] = Set("netflix")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def shouldProcessItem(
    item: NetflixOriginalScrapeItem
  ): Boolean = {
    item.availableLocalDate.exists(_.isBefore(farIntoTheFuture))
  }
}

@JsonCodec
case class NetflixOriginalScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  status: String,
  `type`: ItemType,
  externalId: Option[String])
    extends ScrapedItem {
  override def category: Option[String] = None

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override def description: Option[String] = None
}
