package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import io.circe.generic.auto._
import javax.inject.Inject
import java.time.{Instant, ZoneId, ZoneOffset}

object IngestHboChanges extends IngestJobApp[IngestHboChanges]

class IngestHboChanges @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val storage: Storage,
  protected val networkCache: NetworkCache)
    extends IngestJob[HboScrapeItem] {
  override protected def networkNames: Set[String] = Set("hbo-now", "hbo-go")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Eastern").getRules.getOffset(Instant.now())
}

case class HboScrapeItem(
  availableDate: String,
  title: String,
  releaseYear: Option[String],
  category: String,
  network: String,
  status: String)
    extends ScrapedItem {
  override def isMovie: Boolean = category.toLowerCase().trim() == "film"

  override def isTvShow: Boolean = false
}
