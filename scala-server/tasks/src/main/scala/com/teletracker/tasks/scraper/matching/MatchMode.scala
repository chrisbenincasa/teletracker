package com.teletracker.tasks.scraper.matching

import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
import scala.concurrent.Future

trait MatchMode {
  def lookup[T <: ScrapedItem](
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])]
}
