package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.{EsItem, ItemLookup}
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.scraper.{model, IngestJobArgsLike, ScrapedItem}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchExactTitleLookup @Inject()(
  itemSearch: ItemLookup
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
    for {
      (exactMatches, nonMatchesFromExact) <- getMatches(
        items,
        includeType = true
      )
      (nonTypeMatches, nonMatches) <- getMatches(
        nonMatchesFromExact,
        includeType = false
      )
    } yield {
      exactMatches ++ nonTypeMatches -> nonMatches
    }
  }

  private def getMatches[T <: ScrapedItem](
    items: List[T],
    includeType: Boolean
  ) = {
    val itemsByTitle = items.map(item => item.title -> item).toMap

    val titleTriples = items.map(item => {
      (
        item.title,
        item.thingType.filter(_ => includeType),
        item.releaseYear.map(ry => (ry - 1) to (ry + 1))
      )
    })

    itemSearch
      .lookupItemsByTitleMatch(titleTriples)
      .map(matchesByTitle => {
        val (actualMatchesByTitle, nonMatches) = matchesByTitle.foldLeft(
          (List.empty[(T, EsItem)] -> List.empty[(T, EsItem)])
        ) {
          // If we've found a case-insensitive title match then add it to the positive match set
          case ((matches, nonMatches), (title, esItem))
              if title.equalsIgnoreCase(
                esItem.original_title.getOrElse("")
              ) || esItem.title.get.exists(title.equalsIgnoreCase) =>
            val newMatches = matches ++ (itemsByTitle
              .get(title)
              .map(_ -> esItem))
              .toList

            newMatches -> nonMatches

          // Add non matches to the fallback set
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
            model.MatchResult(
              scrapedItem,
              esItem
            )
        }

        matchResultsForTitles -> (missing ++ nonMatches.map(_._1))
      })
  }
}
