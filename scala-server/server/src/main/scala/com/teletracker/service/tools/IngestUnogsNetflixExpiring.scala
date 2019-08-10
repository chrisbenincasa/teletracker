package com.teletracker.service.tools

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import io.circe.generic.auto._
import javax.inject.Inject

class IngestUnogsNetflixExpiring @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val storage: Storage,
  protected val networkCache: NetworkCache)
    extends IngestJob[UnogsScrapeItem] {
  override protected def networkNames: Set[String] = Set("netflix")
}

case class UnogsScrapeItem(
  availableDate: String,
  title: String,
  releaseYear: Option[String],
  network: String,
  status: String,
  `type`: ThingType)
    extends ScrapedItem {
  override def category: String = ""

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show
}
