package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.model.{EsItem, EsPerson, EsUserItem}
import com.teletracker.common.util.AsyncStream
import io.circe.Json
import javax.inject.Inject
import org.elasticsearch.action.search.{
  SearchRequest,
  SearchResponse,
  SearchScrollRequest
}
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext

abstract class Scroller[T](
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def indexName: String

  def start(
    query: QueryBuilder,
    ttl: TimeValue = TimeValue.timeValueMinutes(5)
  ): AsyncStream[T] = {
    val request =
      new SearchRequest(indexName)
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
        logger.debug(s"Got ${res.getHits.getHits.length} hits on scroll...")

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
        logger.debug("Pulling next scroll page...")

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

abstract class RawJsonScroller(
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[Json](elasticsearchExecutor)
    with ElasticsearchAccess {
  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[Json] = {
    searchResponse.getHits.getHits
      .flatMap(hit => {
        io.circe.parser.parse(hit.getSourceAsString) match {
          case Left(_)      => None
          case Right(value) => Some(value)
        }
      })
      .toList
  }
}

final class ItemsScroller @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsItem](elasticsearchExecutor)
    with ElasticsearchAccess {

  override protected def indexName: String =
    teletrackerConfig.elasticsearch.items_index_name

  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsItem] = {
    searchResponseToItems(searchResponse).items
  }
}

final class PeopleScroller @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsPerson](elasticsearchExecutor)
    with ElasticsearchAccess {

  override protected def indexName: String =
    teletrackerConfig.elasticsearch.people_index_name

  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsPerson] = {
    searchResponseToPeople(searchResponse).items
  }
}

final class UserItemsScroller @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends Scroller[EsUserItem](elasticsearchExecutor)
    with ElasticsearchAccess {

  override protected def indexName: String =
    teletrackerConfig.elasticsearch.user_items_index_name

  override protected def parseResponse(
    searchResponse: SearchResponse
  ): List[EsUserItem] = {
    searchResponseToUserItems(searchResponse).items
  }
}
