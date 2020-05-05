package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.util.Folds
import com.teletracker.tasks.scraper.model.{MatchInput, MatchResult}
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DirectLookupMethod @Inject()(
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[MatchInput[ScrapedItem]] {
  override def apply(
    v1: List[MatchInput[ScrapedItem]],
    v2: IngestJobArgsLike
  ): Future[
    (
      List[MatchResult[MatchInput[ScrapedItem]]],
      List[MatchInput[ScrapedItem]]
    )
  ] = {
    itemLookup
      .lookupItemsByIds(v1.map(_.esItem.id).toSet)
      .map(results => {
        v1.foldLeft(
          Folds
            .list2Empty[MatchResult[MatchInput[ScrapedItem]], MatchInput[
              ScrapedItem
            ]]
        ) {
          case ((matches, nonMatches), input) =>
            results.get(input.esItem.id).flatten match {
              case Some(value) =>
                (matches :+ MatchResult(input, value)) -> nonMatches
              case None =>
                matches -> (nonMatches :+ input)
            }
        }
      })
  }
}
