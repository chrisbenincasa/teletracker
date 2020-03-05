package com.teletracker.tasks.scraper.matching
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.tasks.scraper.{IngestJobArgsLike, ScrapedItem}
import com.teletracker.tasks.scraper.model.MatchResult
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchDirectLookup @Inject()(
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends MatchMode {
  override def lookup[T <: ScrapedItem](
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    val itemByActualId = items.collect {
      case item if item.actualItemId.isDefined => item.actualItemId.get -> item
    }.toMap

    itemLookup
      .getItemsById(itemByActualId.keySet)
      .map(foundItems => {
        val matches = foundItems.toList.collect {
          case (id, Some(item)) => MatchResult(itemByActualId(id), item)
        }

        val missingItems =
          itemByActualId.filterKeys(foundItems.keySet.contains).values

        matches -> missingItems.toList
      })
  }
}
