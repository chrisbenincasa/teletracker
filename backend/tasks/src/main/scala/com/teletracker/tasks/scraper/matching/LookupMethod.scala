package com.teletracker.tasks.scraper.matching

import com.teletracker.tasks.scraper.model.{MatchInput, MatchResult}
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import scala.concurrent.{ExecutionContext, Future}

object LookupMethod {
  trait Agnostic {
    def toMethod[T <: ScrapedItem]: LookupMethod[T]
  }

  def unravel[T <: ScrapedItem](
    args: IngestJobArgsLike,
    methods: List[LookupMethod[T]],
    itemsLeftToMatch: List[T],
    matchesAcc: List[MatchResult[T]] = Nil
  )(implicit executionContext: ExecutionContext
  ): Future[(List[MatchResult[T]], List[T])] = {
    methods match {
      case Nil => Future.successful(matchesAcc -> itemsLeftToMatch)
      case head :: tail =>
        head.apply(itemsLeftToMatch, args).flatMap {
          case (matches, nonMatches) =>
            unravel(args, tail, nonMatches, matchesAcc ++ matches)
        }
    }
  }
}

trait LookupMethod[T <: ScrapedItem]
    extends (
      (
        List[T],
        IngestJobArgsLike
      ) => Future[(List[MatchResult[T]], List[T])]
    ) {}
