package com.teletracker.tasks.scraper

import cats.Parallel
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}

class IngestNetflixOriginalsArrivals @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[NetflixOriginalScrapeItem]
    with IngestJobWithElasticsearch[NetflixOriginalScrapeItem] {

  private val farIntoTheFuture = LocalDate.now().plusYears(1)

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode = elasticsearchLookup

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
  `type`: ThingType,
  externalId: Option[String])
    extends ScrapedItem {
  override def category: String = ""

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show
}
