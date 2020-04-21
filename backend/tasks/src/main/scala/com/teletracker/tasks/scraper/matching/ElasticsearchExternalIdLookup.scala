package com.teletracker.tasks.scraper.matching
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemSearch}
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import com.teletracker.tasks.scraper.model.MatchResult
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object ElasticsearchExternalIdLookup {
  class Factory @Inject()(
    itemLookup: ItemLookup
  )(implicit executionContext: ExecutionContext) {
    def create[T <: ScrapedItem](
      externalSource: ExternalSource,
      getUniqueKey: T => String
    ): ElasticsearchExternalIdLookup[T] = {
      new ElasticsearchExternalIdLookup(
        itemLookup,
        externalSource,
        getUniqueKey
      )
    }
  }
}

class ElasticsearchExternalIdLookup[T <: ScrapedItem](
  itemLookup: ItemLookup,
  externalSource: ExternalSource,
  getUniqueKey: T => String
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[T] {
  override def apply(
    items: List[T],
    v2: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val itemsByExternalId = items
      .map(item => {
        getUniqueKey(item) -> item
      })
      .toMap

    val externalIdsToLookup = itemsByExternalId.collect {
      case (externalId, item) if item.thingType.isDefined =>
        (externalSource, externalId, item.thingType.get)
    }

    itemLookup
      .lookupItemsByExternalIds(externalIdsToLookup.toList)
      .map(found => {
        val foundExternalIds = found.keySet.map(_._2)
        val missingKeys = itemsByExternalId.keySet -- foundExternalIds

        val matchResults = found.toList.flatMap {
          case ((_, externalId), esItem) =>
            itemsByExternalId
              .get(externalId)
              .map(item => {
                MatchResult(item, esItem)
              })
        }

        val missingItems = missingKeys.toList.flatMap(itemsByExternalId.get)

        matchResults -> missingItems
      })
  }
}
