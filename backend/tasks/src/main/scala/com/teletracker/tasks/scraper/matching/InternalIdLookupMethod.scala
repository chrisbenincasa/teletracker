package com.teletracker.tasks.scraper.matching

import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.model.scraping.{
  MatchResult,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.{AsyncStream, Folds}
import com.teletracker.tasks.scraper.IngestJobArgsLike
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object InternalIdLookupMethod {
  class Factory @Inject()(
    itemLookup: ItemLookup
  )(implicit executionContext: ExecutionContext) {
    def create[
      T <: ScrapedItem: ScrapedItemAvailabilityDetails
    ]: InternalIdLookupMethod[T] =
      new InternalIdLookupMethod[T](itemLookup)
  }
}

class InternalIdLookupMethod[T <: ScrapedItem: ScrapedItemAvailabilityDetails](
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[T] {
  override def apply(
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val (hasInternalId, doesntHaveInternalId) =
      items.partition(_.internalId.isDefined)

    if (hasInternalId.isEmpty) {
      Future.successful(Nil -> doesntHaveInternalId)
    } else {
      AsyncStream
        .fromIterable(hasInternalId)
        .grouped(10)
        .mapF(items => {
          val itemsByInternalIds = items.groupBy(_.internalId.get)
          val internalIds = itemsByInternalIds.keySet
          itemLookup
            .lookupItemsByIds(internalIds)
            .map(found => {
              found.foldLeft(Folds.list2Empty[MatchResult[T], T]) {
                case ((matches, nonMatches), (uuid, Some(item))) =>
                  val newMatches = itemsByInternalIds
                    .getOrElse(uuid, Seq.empty)
                    .map(t => MatchResult(t, item))
                  (matches ++ newMatches) -> nonMatches

                case ((matches, nonMatches), (uuid, None)) =>
                  matches -> (nonMatches ++ itemsByInternalIds
                    .getOrElse(uuid, Seq.empty))
              }
            })
        })
        .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
    }
  }
}
