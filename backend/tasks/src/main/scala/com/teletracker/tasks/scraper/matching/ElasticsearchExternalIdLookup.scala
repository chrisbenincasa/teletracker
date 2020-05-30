package com.teletracker.tasks.scraper.matching
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{ItemLookup, ItemSearch}
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.tasks.scraper.IngestJobArgsLike
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
      createOpt(externalSource, getUniqueKey.andThen(Some(_)))
    }

    def createOpt[T <: ScrapedItem](
      externalSource: ExternalSource,
      getUniqueKey: T => Option[String]
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
  getUniqueKey: T => Option[String]
)(implicit executionContext: ExecutionContext)
    extends LookupMethod[T] {
  override def apply(
    items: List[T],
    v2: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val (itemsWithUniqueId, itemsWithoutUniqueId) =
      items.partition(getUniqueKey(_).isDefined)

    if (itemsWithUniqueId.isEmpty) {
      Future.successful(Nil -> itemsWithoutUniqueId)
    } else {
      val itemsByExternalId = itemsWithUniqueId
        .filter(_.thingType.isDefined)
        .map(item => {
          (getUniqueKey(item).get, item.thingType.get) -> item
        })
        .toMap

      val externalIdsToLookup = itemsByExternalId.collect {
        case ((externalId, itemType), item) if item.thingType.isDefined =>
          (externalSource, externalId, itemType)
      }

      itemLookup
        .lookupItemsByExternalIds(externalIdsToLookup.toList)
        .map(found => {
          val missingKeys = itemsByExternalId.keySet -- found.keySet.map {
            case (id, itemType) => id.id -> itemType
          }

          val matchResults = found.toList.flatMap {
            case ((externalId, itemType), esItem) =>
              itemsByExternalId
                .get(externalId.id -> itemType)
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
