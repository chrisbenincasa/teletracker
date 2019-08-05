package com.teletracker.service.tools

import com.google.inject.Module
import com.teletracker.service.inject.Modules
import io.circe.generic.auto._
import java.time.{Instant, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

object IngestHuluChanges extends IngestJob[HuluScrapeItem] {
  override protected def modules: Seq[Module] = Modules()

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())
}

case class HuluScrapeItem(
  availableDate: String,
  title: String,
  releaseYear: Option[String],
  notes: String,
  category: String,
  network: String,
  status: String)
    extends ScrapedItem {
  override def isMovie: Boolean = category.toLowerCase().trim() == "film"

  override def isTvShow: Boolean = !isMovie
}
