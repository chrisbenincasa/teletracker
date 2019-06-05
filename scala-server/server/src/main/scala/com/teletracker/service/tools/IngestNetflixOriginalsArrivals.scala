package com.teletracker.service.tools

import com.teletracker.service.util.json.circe._
import com.teletracker.service.db.model.ThingType
import io.circe.generic.auto._
import java.time.{Instant, ZoneId, ZoneOffset}

object IngestNetflixOriginalsArrivals
    extends IngestJob[NetflixOriginalScrapeItem] {
  override protected def networkNames: Set[String] = Set("netflix")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())
}

case class NetflixOriginalScrapeItem(
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
