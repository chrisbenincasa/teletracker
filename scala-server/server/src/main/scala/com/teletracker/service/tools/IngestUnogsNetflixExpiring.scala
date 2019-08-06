package com.teletracker.service.tools

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.util.json.circe._
import io.circe.generic.auto._

object IngestUnogsNetflixExpiring extends IngestJob[UnogsScrapeItem] {
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
