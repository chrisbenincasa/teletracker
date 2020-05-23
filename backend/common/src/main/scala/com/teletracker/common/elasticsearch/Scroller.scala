package com.teletracker.common.elasticsearch

import com.teletracker.common.elasticsearch.model.{EsItem, EsPerson, EsUserItem}
import com.teletracker.common.util.AsyncStream
import javax.inject.Inject
import org.elasticsearch.action.search.{
  SearchRequest,
  SearchResponse,
  SearchScrollRequest
}
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import scala.concurrent.ExecutionContext

abstract class Scroller[T](
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext) {
  def start(
    index: String,
    query: QueryBuilder,
    ttl: TimeValue = TimeValue.timeValueMinutes(5)
  ): AsyncStream[T] = {
    val request =
      new SearchRequest(index)
        .source(new SearchSourceBuilder().query(query))
        .scroll(ttl)

    AsyncStream
      .fromFuture(elasticsearchExecutor.search(request))
      .flatMap(res => {
        scroll0(parseResponse(res), res, Option(res.getScrollId))
      })
  }

  protected def parseResponse(searchResponse: SearchResponse): List[T]

  // We have more items if the current buffer is still full or if we have processed, in total, less than the number of hits.
  final private def hasNext(
    curr: List[T],
    lastResponse: SearchResponse,
    seen: Long
  ): Boolean = {
    curr.nonEmpty || seen < lastResponse.getHits.getTotalHits.value
  }

  // Continues the scroll given a scrollId
  final private def continue(
    scrollId: String,
    processed: Long
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[T] = {
    AsyncStream
      .fromFuture(
        elasticsearchExecutor.scroll(
          new SearchScrollRequest(scrollId)
            .scroll(TimeValue.timeValueMinutes(5))
        )
      )
      .flatMap(res => {
        scroll0(
          parseResponse(res),
          res,
          Option(res.getScrollId),
          processed
        )
      })
  }

  // Handle the next iteration of the scroll - either we use the buffered CUIDs from the last iteration or
  // continue the scroll query
  final private def scroll0(
    curr: List[T],
    lastResponse: SearchResponse,
    scrollId: Option[String] = None,
    seen: Long = 0
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[T] = {
    if (hasNext(curr, lastResponse, seen)) {
      // If the buffer is depleted, continue the scroll
      if (curr.isEmpty) {
        scrollId.map(continue(_, seen)).getOrElse(AsyncStream.empty)
      } else {
        // If we still have items in the buffer, pop off the head and lazily handle the tail
        AsyncStream.of(curr.head) ++ scroll0(
          curr.tail,
          lastResponse,
          scrollId,
          seen + 1
        )
      }
    } else {
      AsyncStream.empty
    }
  }
}

final class ItemsScroller @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsItem](elasticsearchExecutor)
    with ElasticsearchAccess {
  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsItem] = {
    searchResponseToItems(searchResponse).items
  }
}

final class PeopleScroller @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsPerson](elasticsearchExecutor)
    with ElasticsearchAccess {
  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsPerson] = {
    searchResponseToPeople(searchResponse).items
  }
}

final class UserItemsScroller @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsUserItem](elasticsearchExecutor)
    with ElasticsearchAccess {
  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsUserItem] = {
    searchResponseToUserItems(searchResponse).items
  }
}
