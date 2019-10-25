package com.teletracker.tasks.scraper.matching

import com.teletracker.common.db.model.ThingRaw
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
import scala.concurrent.Future

trait MatchMode[T <: ScrapedItem] {
  def lookup(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])]
}
