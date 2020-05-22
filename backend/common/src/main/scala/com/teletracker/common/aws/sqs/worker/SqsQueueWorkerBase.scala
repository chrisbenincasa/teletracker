//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.common.aws.sqs.worker

import com.teletracker.common.aws.sqs.ProcessingFailedException
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.FinishedAction
import com.teletracker.common.pubsub.{EventBase, QueueReader}
import com.teletracker.common.util.Futures._
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.config.core.api.{ReloadableConfig, WatchToken}
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object SqsQueueWorkerBase {
  type Id[A] = A
  type FutureOption[A] = Future[Option[A]]

  sealed trait FinishedAction
  case class Ack(handle: String) extends FinishedAction
  case class ClearVisibility(handle: String) extends FinishedAction
  case object DoNothing extends FinishedAction
}

abstract class SqsQueueWorkerBase[
  T <: EventBase: Manifest,
  Wrapper[_],
  ReturnWrapper[_]
](
  queue: QueueReader[T],
  reloadable: ReloadableConfig[SqsQueueWorkerConfig]
)(implicit
  executionContext: ExecutionContext) {
  protected val logger = LoggerFactory.getLogger(getClass)

  private val queueName = queue.name

  protected val MONITOR_BASE = s"objects.queueListeners.$queueName."

  protected val MONITOR_EXCEPTION_BASE = MONITOR_BASE + "exceptions."

  @volatile protected var stopped = false
  @volatile protected var started = false
  @volatile private var configWatchToken: WatchToken = _

  def run(): Unit = {
    synchronized {
      if (started) return

      logger.info(
        s"Starting queue worker on ${queue.url} with config: ${reloadable.currentValue()}"
      )

      configWatchToken = reloadable.watch() { newValue =>
        logger.info(
          s"Updated config: ${newValue}"
        )
      }

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

      if (configWatchToken ne null) {
        configWatchToken.cancel()
      }

      started = false
    }

    flush()
  }

  protected def runInternal(): Unit

  protected def process(msg: Wrapper[T]): ReturnWrapper[Wrapper[FinishedAction]]

  final protected val defaultErrorHandler: PartialFunction[Throwable, Unit] = {
    // Note: you can't catch an instance of ProcessingFailedException as it is a singleton
    case ProcessingFailedException => {
      val currentConfig = reloadable.currentValue()

      logger.info(
        s"Processing failed. " +
          s"We won't delete the items from the queue, but will wait ${currentConfig.sleepDurationBetweenFailures.toSeconds} seconds before proceeding."
      )
      sleep(currentConfig.sleepDurationBetweenFailures)
    }
    case NonFatal(e) => {
      val currentConfig = reloadable.currentValue()

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

  protected def dequeue(
    batchSize: Int = reloadable.currentValue().batchSize
  ): List[T] = {
    try {
      logger.debug(s"Dequeueing ${batchSize} items")
      queue
        .dequeue(batchSize, reloadable.currentValue().waitForMessageTime)
        .await()
    } catch errorHandler andThen (_ => Nil)
  }

  protected def changeVisibilityAsync(
    handles: List[String],
    duration: Option[FiniteDuration]
  ): Future[Unit] = {
    if (handles.nonEmpty) {
      (duration match {
        case Some(value) => queue.changeVisibility(handles, value).map(_ => {})
        case None        => queue.clearVisibility(handles)
      })
    } else {
      Future.unit
    }
  }

  protected def changeVisibility(
    handles: List[String],
    duration: Option[FiniteDuration]
  ): Unit = changeVisibilityAsync(handles, duration).await()

  protected def ack(handles: List[String]): Unit = {
    ackAsync(handles).await()
  }

  protected def ackAsync(handles: List[String]): Future[Unit] = {
    if (handles.nonEmpty) {
      logger.debug(s"Acking ${handles.size} messages.")
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
    sleep(reloadable.currentValue().sleepDurationBetweenFailures)

  final protected def sleepOnEmpty(): Unit =
    sleep(reloadable.currentValue().sleepDurationBetweenEmptyBatches)

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
  val heartbeat: Option[HeartbeatConfig] = None) {

  override def toString =
    s"SqsQueueWorkerConfig(batchSize=$batchSize, sleepDurationBetweenFailures=$sleepDurationBetweenFailures, waitForMessageTime=$waitForMessageTime, sleepDurationBetweenEmptyBatches=$sleepDurationBetweenEmptyBatches, heartbeat=$heartbeat)"
}
