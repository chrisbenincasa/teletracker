package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.{EsItem, ItemSearch}
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchExactTitleLookup @Inject()(
  itemSearch: ItemSearch
)(implicit executionContext: ExecutionContext)
    extends LookupMethod.Agnostic { self =>
  override def toMethod[T <: ScrapedItem]: LookupMethod[T] =
    new LookupMethod[T] {
      override def apply(
        items: List[T],
        args: IngestJobArgsLike
      ): Future[(List[MatchResult[T]], List[T])] = self.apply(items, args)
    }

  def apply[T <: ScrapedItem](
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val itemsByTitle = items.map(item => item.title -> item).toMap

    val titleTriples = items.map(item => {
      (
        item.title,
        Some(item.thingType),
        item.releaseYear.map(ry => (ry - 1) to (ry + 1))
      )
    })

    itemSearch
      .lookupItemsByTitleMatch(titleTriples)
      .map(matchesByTitle => {
        val (actualMatchesByTitle, nonMatches) = matchesByTitle.foldLeft(
          (List.empty[(T, EsItem)] -> List.empty[(T, EsItem)])
        ) {
          case ((matches, nonMatches), (title, esItem))
              if title.equalsIgnoreCase(
                esItem.original_title.getOrElse("")
              ) || title.equalsIgnoreCase(esItem.title.get.head) =>
            val newMatches = matches ++ (itemsByTitle
              .get(title)
              .map(_ -> esItem))
              .toList

            newMatches -> nonMatches

          case ((matches, nonMatches), (title, esItem)) =>
            val newNonMatches = nonMatches ++ (itemsByTitle
              .get(title)
              .map(_ -> esItem))
              .toList
            matches -> newNonMatches
        }

        val missing =
          (itemsByTitle.keySet -- matchesByTitle.keySet)
            .flatMap(itemsByTitle.get)
            .toList

        val matchResultsForTitles = actualMatchesByTitle.map {
          case (scrapedItem, esItem) =>
            MatchResult(
              scrapedItem,
              esItem.id,
              esItem.title.get.head
            )
        }

        matchResultsForTitles -> (missing ++ nonMatches.map(_._1))
      })
  }
}
