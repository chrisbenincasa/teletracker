package com.teletracker.common.elasticsearch

import javax.inject.Inject
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.get.{
  GetRequest,
  GetResponse,
  MultiGetRequest,
  MultiGetResponse
}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.search.{
  MultiSearchRequest,
  MultiSearchResponse,
  SearchRequest,
  SearchResponse
}
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.client.core.{CountRequest, CountResponse}
import org.elasticsearch.client.{RequestOptions, RestHighLevelClient}
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import scala.concurrent.{Future, Promise}

class ElasticsearchExecutor @Inject()(client: RestHighLevelClient) {
  def get(request: GetRequest): Future[GetResponse] = {
    withListener(client.getAsync(request, RequestOptions.DEFAULT, _))
  }

  def multiGet(request: MultiGetRequest): Future[MultiGetResponse] = {
    withListener(client.mgetAsync(request, RequestOptions.DEFAULT, _))
  }

  def index(request: IndexRequest): Future[IndexResponse] = {
    withListener(client.indexAsync(request, RequestOptions.DEFAULT, _))
  }

  def update(request: UpdateRequest): Future[UpdateResponse] = {
    withListener(client.updateAsync(request, RequestOptions.DEFAULT, _))
  }

  def updateByQuery(
    request: UpdateByQueryRequest
  ): Future[BulkByScrollResponse] = {
    withListener(client.updateByQueryAsync(request, RequestOptions.DEFAULT, _))
  }

  def search(request: SearchRequest): Future[SearchResponse] = {
    withListener(client.searchAsync(request, RequestOptions.DEFAULT, _))
  }

  def multiSearch(request: MultiSearchRequest): Future[MultiSearchResponse] = {
    withListener(client.msearchAsync(request, RequestOptions.DEFAULT, _))
  }

  def count(request: CountRequest): Future[CountResponse] = {
    withListener(client.countAsync(request, RequestOptions.DEFAULT, _))
  }

  def withListener[T](f: ActionListener[T] => Unit): Future[T] = {
    val (listener, promise) = makeListener[T]
    f(listener)
    promise.future
  }

  private def makeListener[T]: (ActionListener[T], Promise[T]) = {
    val promise = Promise[T]
    val listener = new ActionListener[T] {
      override def onResponse(response: T): Unit =
        promise.trySuccess(response)

      override def onFailure(e: Exception): Unit = promise.tryFailure(e)
    }
    (listener, promise)
  }
}
