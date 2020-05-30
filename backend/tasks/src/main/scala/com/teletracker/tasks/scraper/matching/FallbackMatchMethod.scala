package com.teletracker.tasks.scraper.matching

import com.teletracker.common.model.scraping.{NonMatchResult, ScrapedItem}
import com.teletracker.tasks.scraper.IngestJobArgsLike
import scala.concurrent.{ExecutionContext, Future}

//object FallbackMatchMethod {
//  trait Agnostic {
//    def toMethod[T <: ScrapedItem]: FallbackMatchMethod[T]
//  }
//
//  def unravel[T <: ScrapedItem](
//    args: IngestJobArgsLike,
//    methods: List[FallbackMatchMethod[T]],
//    itemsLeftToMatch: List[T],
//    matchesAcc: List[MatchResult[T]] = Nil
//  )(implicit executionContext: ExecutionContext
//  ): Future[(List[MatchResult[T]], List[T])] = {
//    methods match {
//      case Nil => Future.successful(matchesAcc -> itemsLeftToMatch)
//      case head :: tail =>
//        head.apply(args, itemsLeftToMatch).flatMap(results => {
//          unravel(args, tail, results)
//        })
//    }
//  }
//}

trait FallbackMatchMethod[T <: ScrapedItem]
    extends (
      (
        IngestJobArgsLike,
        List[T]
      ) => Future[List[NonMatchResult[T]]]
    )
