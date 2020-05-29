package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.{
  ElasticsearchItemsResponse,
  FuzzyItemLookupRequest,
  ItemLookup
}
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.{Folds, Sanitizers}
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.scraper.{model, IngestJobArgsLike}
import com.teletracker.tasks.util.Stopwords
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchExactTitleLookup @Inject()(
  itemSearch: ItemLookup,
  stopwords: Stopwords
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
  ): Future[(List[MatchResult[T]], List[T])] = {
    val requestsAndItems = items.map(item => {
      FuzzyItemLookupRequest(
        title = item.title,
        description = item.description,
        itemType = item.thingType.filter(_ => includeType),
        releaseYearRange = item.releaseYear.map(ry => (ry - 1) to (ry + 1)),
        looseReleaseYearMatching = true
      ) -> item
    })

    val itemByRequestId = requestsAndItems.map {
      case (request, item) => request.id -> item
    }.toMap

    itemSearch
      .lookupFuzzyBatch(requestsAndItems.map(_._1))
      .map(results => {
        itemByRequestId.foldLeft(Folds.list2Empty[MatchResult[T], T]) {
          case ((matches, nonMatches), (requestId, scrapedItem)) =>
            results
              .get(requestId)
              .flatMap(findSuitableMatch(_, scrapedItem)) match {
              case Some(esItem) =>
                (matches :+ MatchResult(scrapedItem, esItem)) -> nonMatches
              case None => matches -> (nonMatches :+ scrapedItem)
            }
        }
      })

//    itemSearch
//      .lookupFuzzy(requests)
//      .map(matchesByTitle => {
//        val (actualMatchesByTitle, nonMatches) = matchesByTitle.foldLeft(
//          (List.empty[(T, EsItem)] -> List.empty[(T, EsItem)])
//        ) {
//          // If we've found a case-insensitive title match then add it to the positive match set
//          case ((matches, nonMatches), (title, esItem))
//              if
////              title.equalsIgnoreCase(
////                esItem.original_title.getOrElse("")
////              ) ||
//              esItem.title.get.exists(title.equalsIgnoreCase) =>
//            val newMatches = matches ++ (itemsByTitle
//              .get(title)
//              .map(_ -> esItem))
//              .toList
//
//            newMatches -> nonMatches
//
//          // Add non matches to the fallback set
//          case ((matches, nonMatches), (title, esItem)) =>
//            val newNonMatches = nonMatches ++ (itemsByTitle
//              .get(title)
//              .map(_ -> esItem))
//              .toList
//            matches -> newNonMatches
//        }
//
//        val missing =
//          (itemsByTitle.keySet -- matchesByTitle.keySet)
//            .flatMap(itemsByTitle.get)
//            .toList
//
//        val matchResultsForTitles = actualMatchesByTitle.map {
//          case (scrapedItem, esItem) =>
//            model.MatchResult(
//              scrapedItem,
//              esItem
//            )
//        }
//
//        matchResultsForTitles -> (missing ++ nonMatches.map(_._1))
//      })
  }

  private def findSuitableMatch[T <: ScrapedItem](
    response: ElasticsearchItemsResponse,
    item: T
  ): Option[EsItem] = {
    val sanitizedTargetTitle =
      stopwords.removeStopwords(Sanitizers.normalizeQuotes(item.title))
    response.items.find(esItem => {
      val exactTitleMatch = stopwords
        .removeStopwords(esItem.sanitizedTitle)
        .equalsIgnoreCase(sanitizedTargetTitle)
      val hasAltTitleMatch = esItem.alternative_titles
        .getOrElse(Nil)
        .filter(_.country_code == "US")
        .map(_.title)
        .map(stopwords.removeStopwords)
        .exists(
          _.equalsIgnoreCase(sanitizedTargetTitle)
        )

      exactTitleMatch || hasAltTitleMatch
    })
  }
}
