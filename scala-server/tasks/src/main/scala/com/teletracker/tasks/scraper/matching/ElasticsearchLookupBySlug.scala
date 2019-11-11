package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.util.Slug
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
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

        val lookupTriples = itemsBySlug.map {
          case (slug, item) =>
            (
              slug,
              item.thingType,
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
                      MatchResult(
                        scrapedItem,
                        esItem.id,
                        esItem.title.get.head
                      )
                  )
            }

            matchResultsForSlugs.toList -> (missing ++ withoutReleaseYear)
          })
      }
    }
  }
}
