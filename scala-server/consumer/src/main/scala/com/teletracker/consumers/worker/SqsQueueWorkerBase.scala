//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.consumers.worker

import com.teletracker.common.pubsub.EventBase
import com.teletracker.common.util.Futures._
import com.teletracker.consumers.ProcessingFailedException
import com.teletracker.consumers.worker.poll.HeartbeatConfig
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object SqsQueueWorkerBase {
  type Id[A] = A
  type FutureOption[A] = Future[Option[A]]
}

abstract class SqsQueueWorkerBase[
  T <: EventBase: Manifest,
  Wrapper[_],
  ReturnWrapper[_]
](
  queue: QueueReader[T],
  reloadable: SqsQueueWorkerConfig
)(implicit
  executionContext: ExecutionContext) {
  protected val logger = LoggerFactory.getLogger(getClass)

  private val queueName = queue.name

  protected val MONITOR_BASE = s"objects.queueListeners.$queueName."

  protected val MONITOR_EXCEPTION_BASE = MONITOR_BASE + "exceptions."

  @volatile protected var stopped = false
  @volatile protected var started = false

  def run(): Unit = {
    synchronized {
      if (started) return

      logger.info(s"Starting queue worker on ${queue.url}")

      started = true
    }

    runInternal()
  }

  def stop(): Future[Unit] = {
    synchronized {
      if (stopped) {
        return Future.successful(Unit)
      }

      logger.info(s"Stopping queue worker on ${queue.url}")

      stopped = true

      started = false
    }

    flush()
  }

  protected def runInternal(): Unit

  protected def process(msg: Wrapper[T]): ReturnWrapper[Wrapper[String]]

  final protected val defaultErrorHandler: PartialFunction[Throwable, Unit] = {
    // Note: you can't catch an instance of ProcessingFailedException as it is a singleton
    case ProcessingFailedException => {
      val currentConfig = reloadable

      logger.info(
        s"Processing failed. " +
          s"We won't delete the items from the queue, but will wait ${currentConfig.sleepDurationBetweenFailures.toSeconds} seconds before proceeding."
      )
      sleep(currentConfig.sleepDurationBetweenFailures)
    }
    case NonFatal(e) => {
      val currentConfig = reloadable

      e.printStackTrace()
      logger.error("Uncaught exception!", e)

      sleep(currentConfig.sleepDurationBetweenFailures)
    }
  }

  protected def errorHandler: PartialFunction[Throwable, Unit] =
    defaultErrorHandler

  protected object CanHandleError {
    def unapply(ex: Throwable): Option[Throwable] =
      Some(ex).filter(errorHandler.isDefinedAt)
  }

  protected def dequeue(batchSize: Int = reloadable.batchSize): List[T] = {
    try {
      queue.dequeue(batchSize, reloadable.waitForMessageTime).await()
    } catch errorHandler andThen (_ => Nil)
  }

  protected def ack(handles: List[String]): Unit = {
    if (handles.nonEmpty) {
      try {
        queue.remove(handles).await()
      } catch errorHandler
    }
  }

  protected def ackAsync(handles: List[String]): Future[Unit] = {
    if (handles.nonEmpty) {
      queue.remove(handles).recover(errorHandler)
    } else Future.successful({})
  }

  protected def sleep(duration: Duration): Unit = {
    try {
      Thread.sleep(duration.toMillis)
    } catch {
      case _: Exception =>
    }
  }

  final protected def sleepOnFail(): Unit =
    sleep(reloadable.sleepDurationBetweenFailures)

  final protected def sleepOnEmpty(): Unit =
    sleep(reloadable.sleepDurationBetweenEmptyBatches)

  /**
    * An awaitable method to indicate that the current batch of processing has completed
    *
    * @return
    */
  protected def flush(): Future[Unit]
}

class SqsQueueWorkerConfig(
  val batchSize: Int = 10,
  val sleepDurationBetweenFailures: Duration = 30 seconds,
  val waitForMessageTime: Duration = 1 second,
  val sleepDurationBetweenEmptyBatches: Duration = 1 second,
  val heartbeat: Option[HeartbeatConfig] = None)
