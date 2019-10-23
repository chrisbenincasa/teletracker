package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.elasticsearch.{ItemSearch, ItemUpdater}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, ZoneId, ZoneOffset}

object IngestHboChanges extends IngestJobApp[IngestHboChanges]

object HboScrapeItem

class IngestHboChanges @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemSearch,
  protected val itemUpdater: ItemUpdater)
    extends IngestJob[HboScrapeItem]
    with IngestJobWithElasticsearch[HboScrapeItem] {

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def networkNames: Set[String] = Set("hbo-now", "hbo-go")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Eastern").getRules.getOffset(Instant.now())
}

case class HboScrapeItem(
  availableDate: Option[String],
  title: String,
  parsedReleaseYear: Option[Int],
  category: String,
  network: String,
  status: String,
  externalId: Option[String])
    extends ScrapedItem {
  override def isMovie: Boolean = category.toLowerCase().trim() == "film"

  override def isTvShow: Boolean = false

  override def releaseYear: Option[Int] = parsedReleaseYear
}
