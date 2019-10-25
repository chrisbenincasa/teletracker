package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemSearch
import com.teletracker.common.util.Slug
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem
}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchLookup[T <: ScrapedItem] @Inject()(
  itemSearch: ItemSearch
)(implicit executionContext: ExecutionContext)
    extends MatchMode[T] {

  override def lookup(
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

    val itemsByTitle = withReleaseYear.map(item => item.title -> item).toMap

    val lookupTriples = itemsBySlug.map {
      case (slug, item) =>
        (slug, item.thingType, item.releaseYear.map(ry => (ry - 1) to (ry + 1)))
    }.toList

    itemSearch
      .lookupItemsBySlug(lookupTriples)
      .flatMap(foundBySlug => {
        val missing =
          (itemsBySlug.keySet -- foundBySlug.keySet)
            .flatMap(itemsBySlug.get)
            .toList

        val missingByTitle = missing.map(item => item.title -> item).toMap
        val titleTriples = missing.map(item => {
          (
            item.title,
            Some(item.thingType),
            item.releaseYear.map(ry => (ry - 1) to (ry + 1))
          )
        })

        itemSearch
          .lookupItemsByTitleExact(titleTriples)
          .map(matchesByTitle => {
            val stillMissing =
              (missingByTitle.keySet -- matchesByTitle.keySet)
                .flatMap(missingByTitle.get)
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

            val matchResultsForTitles = matchesByTitle.flatMap {
              case (title, esItem) =>
                itemsByTitle
                  .get(title)
                  .map(
                    scrapedItem =>
                      MatchResult(
                        scrapedItem,
                        esItem.id,
                        esItem.title.get.head
                      )
                  )
            }

            (matchResultsForSlugs ++ matchResultsForTitles).toList -> stillMissing
          })
      })
  }
}
