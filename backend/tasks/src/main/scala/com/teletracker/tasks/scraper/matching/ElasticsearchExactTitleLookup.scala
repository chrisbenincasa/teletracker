package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.{
  ElasticsearchItemsResponse,
  FuzzyItemLookupRequest,
  ItemLookup
}
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.util.{Folds, RelativeRange, Sanitizers}
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
        exactReleaseYear = item.releaseYear.map(_ -> 3.0f),
        releaseYearTiers = Some(Seq(RelativeRange.forInt(1) -> 1.0f)),
        looseReleaseYearMatching = false,
        popularityThreshold = Some(1.0),
        castNames = item.cast.map(_.map(_.name).toSet),
        crewNames = item.crew.map(_.map(_.name).toSet),
        strictTitleMatch = true
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
                (matches :+ scraping
                  .MatchResult(scrapedItem, esItem)) -> nonMatches
              case None => matches -> (nonMatches :+ scrapedItem)
            }
        }
      })
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
