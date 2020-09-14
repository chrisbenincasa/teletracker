package com.teletracker.common.elasticsearch

import com.teletracker.common.util.Retry
import com.teletracker.common.util.execution.{
  ExecutionContextProvider,
  ProvidedSchedulerService
}
import com.twitter.concurrent.NamedPoolThreadFactory
import javax.inject.Inject
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.delete.{DeleteRequest, DeleteResponse}
import org.elasticsearch.action.get.{
  GetRequest,
  GetResponse,
  MultiGetRequest,
  MultiGetResponse
}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.search._
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.client.core.{CountRequest, CountResponse}
import org.elasticsearch.client.{RequestOptions, RestHighLevelClient}
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  DeleteByQueryRequest,
  UpdateByQueryRequest
}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

class ElasticsearchExecutor @Inject()(
  client: RestHighLevelClient
)(implicit executionContext: ExecutionContext) {
  private val defaultRetryOptions =
    Retry.RetryOptions(maxAttempts = 3, initialDuration = 5 seconds, 30 seconds)

  private val schedulerService = ExecutionContextProvider.provider.of(
    Executors.newScheduledThreadPool(5, new NamedPoolThreadFactory("es-retry"))
  )

  def get(request: GetRequest): Future[GetResponse] = {
    withRetryingListener()(client.getAsync(request, RequestOptions.DEFAULT, _))
  }

  def multiGet(request: MultiGetRequest): Future[MultiGetResponse] = {
    withRetryingListener()(client.mgetAsync(request, RequestOptions.DEFAULT, _))
  }

  def index(request: IndexRequest): Future[IndexResponse] = {
    withListener(client.indexAsync(request, RequestOptions.DEFAULT, _))
  }

  def update(request: UpdateRequest): Future[UpdateResponse] = {
    withListener(client.updateAsync(request, RequestOptions.DEFAULT, _))
  }

  def bulk(request: BulkRequest): Future[BulkResponse] = {
    withListener(client.bulkAsync(request, RequestOptions.DEFAULT, _))
  }

  def updateByQuery(
    request: UpdateByQueryRequest
  ): Future[BulkByScrollResponse] = {
    withListener(client.updateByQueryAsync(request, RequestOptions.DEFAULT, _))
  }

  def delete(request: DeleteRequest): Future[DeleteResponse] = {
    withListener(client.deleteAsync(request, RequestOptions.DEFAULT, _))
  }

  def deleteByQuery(
    request: DeleteByQueryRequest
  ): Future[BulkByScrollResponse] = {
    withListener(client.deleteByQueryAsync(request, RequestOptions.DEFAULT, _))
  }

  def search(request: SearchRequest): Future[SearchResponse] = {
    withRetryingListener()(
      client.searchAsync(request, RequestOptions.DEFAULT, _)
    )
  }

  def scroll(request: SearchScrollRequest): Future[SearchResponse] = {
    withRetryingListener()(
      client.scrollAsync(request, RequestOptions.DEFAULT, _)
    )
  }

  def multiSearch(request: MultiSearchRequest): Future[MultiSearchResponse] = {
    withRetryingListener()(
      client.msearchAsync(request, RequestOptions.DEFAULT, _)
    )
  }

  def count(request: CountRequest): Future[CountResponse] = {
    withRetryingListener()(
      client.countAsync(request, RequestOptions.DEFAULT, _)
    )
  }

  protected def withListener[T](f: ActionListener[T] => Unit): Future[T] = {
    val (listener, promise) = makeListener[T]
    f(listener)

    lazy val exception = new ElasticsearchRequestException(
      "Elasticsearch request failed"
    )

    promise.future
      .transform(
        identity,
        e =>
          exception
            .copy(message = s"${exception.message}: ${e.getMessage}", cause = e)
      )
  }

  protected def withRetryingListener[T](
    options: Retry.RetryOptions = defaultRetryOptions
  )(
    f: ActionListener[T] => Unit
  ): Future[T] = {
    val retry = new Retry(schedulerService)

    retry.withRetries(options)(() => withListener(f))
  }

  protected def makeListener[T]: (ActionListener[T], Promise[T]) = {
    val promise = Promise[T]
    val listener = new ActionListener[T] {
      override def onResponse(response: T): Unit =
        promise.trySuccess(response)

      override def onFailure(e: Exception): Unit =
        promise.tryFailure(e)
    }

    (listener, promise)
  }
}

case class ElasticsearchRequestException(
  message: String,
  cause: Throwable)
    extends Exception(message, cause) {
  def this(message: String) {
    this(message, null)
  }
}
