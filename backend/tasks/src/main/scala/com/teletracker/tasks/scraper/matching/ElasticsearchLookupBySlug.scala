package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.util.Slug
import com.teletracker.tasks.scraper.{model, IngestJobArgsLike}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchLookupBySlug @Inject()(
  itemSearch: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod.Agnostic {

  override def toMethod[T <: ScrapedItem]: LookupMethod[T] = {
    new LookupMethod[T] {
      override def apply(
        items: List[T],
        args: IngestJobArgsLike
      ): Future[(List[MatchResult[T]], List[T])] = {
        val (withReleaseYear, withoutReleaseYear) =
          items.partition(_.releaseYear.isDefined)

        val itemsBySlug = withReleaseYear
          .map(item => {
            Slug(item.title, item.releaseYear.get) -> item
          })
          .toMap

        val missingType = itemsBySlug.filter {
          case (_, item) => item.thingType.isEmpty
        }.values

        val lookupTriples = itemsBySlug.collect {
          case (slug, item) if item.thingType.nonEmpty =>
            (
              slug,
              item.thingType.get,
              item.releaseYear.map(ry => (ry - 1) to (ry + 1))
            )
        }.toList

        itemSearch
          .lookupItemsBySlug(lookupTriples)
          .map(foundBySlug => {
            val missing =
              (itemsBySlug.keySet -- foundBySlug.keySet)
                .flatMap(itemsBySlug.get)
                .toList

            val matchResultsForSlugs = foundBySlug.flatMap {
              case (slug, esItem) =>
                itemsBySlug
                  .get(slug)
                  .map(
                    scrapedItem =>
                      scraping.MatchResult(
                        scrapedItem,
                        esItem
                      )
                  )
            }

            matchResultsForSlugs.toList -> (missing ++ withoutReleaseYear ++ missingType)
          })
      }
    }
  }
}
