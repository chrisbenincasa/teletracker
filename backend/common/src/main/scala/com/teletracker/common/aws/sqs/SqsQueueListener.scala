package com.teletracker.common.aws.sqs

import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.pubsub.EventBase
import com.teletracker.common.util.Futures._
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SqsQueueListener[T <: EventBase](
  protected val worker: SqsQueueWorkerBase[T]) {

  protected lazy val logger = LoggerFactory.getLogger(getClass)

  private val workerThread = new Thread(() => {
    worker.run()
  })

  def start(): Unit = {
    synchronized {
      workerThread.setDaemon(true)
      workerThread.setName(
        s"QueueListener-Worker-${worker.getClass.getSimpleName}-${workerThread.getName}"
      )
      workerThread.start()
    }
  }

  def stop(): Future[Unit] = {
    synchronized {
      worker.stop()
    }
  }
}

abstract class ActiveCrawlSqsQueueListener[T <: EventBase](
  crawlerName: CrawlerName,
  crawlStore: CrawlStore,
  worker: SqsQueueWorkerBase[T]
)(implicit executionContext: ExecutionContext)
    extends SqsQueueListener[T](worker) {

  override def start(): Unit = {
    crawlStore
      .waitForActiveCrawlCompletion(crawlerName, None)
      .andThen {
        case Success(value) =>
          value.awaitable.onComplete {
            case Failure(exception) =>
            case Success(_) =>
              stop().await()
          }
      }
      .map(_ => {})
  }
}

case object ProcessingFailedException extends Exception
