package com.teletracker.tasks.scraper.matching
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.tasks.scraper.IngestJobArgsLike
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchDirectLookup @Inject()(
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends LookupMethod.Agnostic {

  override def create[T <: ScrapedItem]: LookupMethod[T] = {
    new LookupMethod[T] {
      override def apply(
        items: List[T],
        args: IngestJobArgsLike
      ): Future[(List[MatchResult[T]], List[T])] = {
        val itemByActualId = items.collect {
          case item if item.actualItemId.isDefined =>
            item.actualItemId.get -> item
        }.toMap

        itemLookup
          .lookupItemsByIds(itemByActualId.keySet)
          .map(foundItems => {
            val matches = foundItems.toList.collect {
              case (id, Some(item)) =>
                scraping.MatchResult(itemByActualId(id), item)
            }

            val missingItems =
              itemByActualId.filterKeys(foundItems.keySet.contains).values

            matches -> missingItems.toList
          })
      }
    }
  }
}
