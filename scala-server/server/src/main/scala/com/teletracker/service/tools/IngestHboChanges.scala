package com.teletracker.service.tools

import io.circe.generic.auto._
import java.time.{Instant, ZoneId, ZoneOffset}

object IngestHboChanges extends IngestJob[HboScrapeItem] {
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
