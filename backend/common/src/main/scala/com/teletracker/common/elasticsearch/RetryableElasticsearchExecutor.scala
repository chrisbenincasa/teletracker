package com.teletracker.common.elasticsearch

import com.teletracker.common.inject.RetryScheduler
import com.teletracker.common.util.Retry
import com.teletracker.common.util.Retry.RetryOptions
import javax.inject.Inject
import org.elasticsearch.action.ActionListener
import org.elasticsearch.client.RestHighLevelClient
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class RetryableElasticsearchExecutor @Inject()(
  client: RestHighLevelClient,
  @RetryScheduler retryScheduler: ScheduledExecutorService
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchExecutor(client) {
  private val retry = new Retry(retryScheduler)

  override protected def withListener[T](
    f: ActionListener[T] => Unit
  ): Future[T] = {
    retry.withRetries(RetryOptions(3, 5 seconds, 20 seconds, {
      case _: SocketTimeoutException => true
    }))(() => {
      super.withListener(f)
    })
  }
}
