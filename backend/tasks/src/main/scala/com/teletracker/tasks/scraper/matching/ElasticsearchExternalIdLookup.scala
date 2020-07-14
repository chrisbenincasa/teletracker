package com.teletracker.tasks.scraper.matching
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.model.EsExternalId
import com.teletracker.common.elasticsearch.{ItemLookup, ItemSearch}
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{
  MatchResult,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.util.Folds
import com.teletracker.tasks.scraper.IngestJobArgsLike
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object ElasticsearchExternalIdLookup {
  class Factory @Inject()(
    itemLookup: ItemLookup
  )(implicit executionContext: ExecutionContext) {
    def create[
      T <: ScrapedItem: ScrapedItemAvailabilityDetails
    ]: ElasticsearchExternalIdLookup[T] = {
      new ElasticsearchExternalIdLookup[T](itemLookup)
    }
  }
}

class ElasticsearchExternalIdLookup[
  T <: ScrapedItem: ScrapedItemAvailabilityDetails
](
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[T] {
  import ScrapedItemAvailabilityDetails.syntax._

  override def apply(
    items: List[T],
    v2: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val (itemsWithUniqueId, itemsWithoutUniqueId) =
      items.partition(_.externalIds.nonEmpty)

    if (itemsWithUniqueId.isEmpty) {
      Future.successful(Nil -> itemsWithoutUniqueId)
    } else {
      val (itemsByExternalId, externalIdsToLookup) = itemsWithUniqueId.foldLeft(
        (
          Map.empty[(EsExternalId, ItemType), T],
          List.empty[(ExternalSource, String, ItemType)]
        )
      ) {
        case ((mapAcc, keysAcc), item) =>
          val externalIds = item.externalIds
          val newMap = mapAcc ++ externalIds.map {
            case (source, str) =>
              (EsExternalId(source, str), item.itemType) -> item
          }

          val newKeys = keysAcc ++ externalIds.map {
            case (source, str) => (source, str, item.itemType)
          }

          (newMap, newKeys)
      }

      itemLookup
        .lookupItemsByExternalIds(externalIdsToLookup)
        .map(found => {
          val missingKeys = itemsByExternalId.keySet -- found.keySet

          val matchResults = found.toList.flatMap {
            case ((externalId, itemType), esItem) =>
              itemsByExternalId
                .get(externalId -> itemType)
                .map(item => {
                  scraping.MatchResult(item, esItem)
                })
          }

          val missingItems = missingKeys.toList.flatMap(itemsByExternalId.get)

          matchResults -> (missingItems ++ itemsWithoutUniqueId)
        })
    }
  }
}
