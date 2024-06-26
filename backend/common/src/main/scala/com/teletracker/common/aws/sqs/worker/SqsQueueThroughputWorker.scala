//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.common.aws.sqs.worker

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.{
  Ack,
  ClearVisibility,
  FinishedAction
}
import com.teletracker.common.pubsub.{EventBase, QueueReader}
import com.teletracker.common.util.execution.{
  ExecutionContextProvider,
  ProvidedSchedulerService
}
import com.teletracker.common.aws.sqs.worker.poll.{HeartbeatConfig, Heartbeats}
import com.teletracker.common.config.core.api.ReloadableConfig
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

sealed trait Process

object SqsQueueThroughputWorker {
  def apply[T <: EventBase: Manifest](
    queue: QueueReader[T],
    config: ReloadableConfig[SqsQueueThroughputWorkerConfig]
  )(
    processFunc: T => Future[FinishedAction]
  )(implicit
    executionContext: ExecutionContext
  ): SqsQueueThroughputWorker[T] = {

    new SqsQueueThroughputWorker(queue, config) {
      override protected def process(msg: T): Future[FinishedAction] =
        processFunc(msg)
    }
  }

  /**
    * Runs the function per message in a queue. If the function succeeds its ack'd, otherwise not
    *
    * @param queue
    * @param config
    * @param processFunc
    * @param executionContext
    * @tparam T
    * @return
    */
  def ofFunction[T <: EventBase: Manifest](
    queue: QueueReader[T],
    config: ReloadableConfig[SqsQueueThroughputWorkerConfig]
  )(
    processFunc: T => Future[FinishedAction]
  )(implicit
    executionContext: ExecutionContext
  ): SqsQueueThroughputWorker[T] = {

    new SqsQueueThroughputWorker(queue, config) {
      override protected def process(msg: T): Future[FinishedAction] = {
        processFunc(msg)
      }
    }
  }
}

abstract class SqsQueueThroughputWorker[T <: EventBase](
  override protected val queue: QueueReader[T],
  reloadableConfig: ReloadableConfig[SqsQueueThroughputWorkerConfig]
)(implicit
  executionContext: ExecutionContext)
    extends SqsQueueWorkerBase[
      T
    ](queue, reloadableConfig)
    with Heartbeats[T] {

  final override type Wrapper[A] = A
  final override type ReturnWrapper[A] = Future[A]

  private val outstanding = new AtomicInteger(0)

  override protected def getConfig: ReloadableConfig[SqsQueueWorkerConfig] =
    reloadableConfig

  def currentlyRunningTasks: Int = outstanding.get()

  override protected def runInternal(): Unit = runInternalFinal()

  protected lazy val heartbeatPool = ExecutionContextProvider.provider.of(
    Executors.newScheduledThreadPool(
      reloadableConfig.currentValue().maxOutstandingItems
    )
  )

  @tailrec
  private def runInternalFinal(): Unit = {
    try {
      if (stopped) return

      val currOutstanding = outstanding.get()

      val config = reloadableConfig.currentValue()

      if (currOutstanding > config.maxOutstandingItems) {
        logger.warn(
          s"Unexpected condition: currOutstanding = $currOutstanding and max = ${config.maxOutstandingItems}"
        )
      }

      if (currOutstanding < config.maxOutstandingItems) {
        val items = dequeue(config.maxOutstandingItems - currOutstanding)

        if (items.isEmpty) {
          logger.debug("No items found. Taking a nap...")

          sleepOnEmpty()
        } else {
          items.foreach(createProcessingFuture)
        }
      } else {
        logger.debug(
          s"Queue full, taking a ${config.sleepDurationWhenQueueFull} nap"
        )
        sleep(config.sleepDurationWhenQueueFull)
      }

    } catch {
      case NonFatal(e) =>
        logger.warn("Got unexpected error within run loop.", e)
        sleepOnFail()
    }

    runInternalFinal()
  }

  /**
    * Kick off a future async for this particular item
    *
    * Keep track of the item not via a reference to its future, but instead
    * by the atomic outstanding counter
    *
    * When the future completes it decrements the atomic counter
    *
    * The function safely wraps the internal {{process}} method
    * so that no exceptions are leaked and the calling loop always continues
    * @param item
    */
  private def createProcessingFuture(item: T): Future[Unit] = {
    outstanding.incrementAndGet()

    try {
      logger.debug(s"Processing item ${item.receipt_handle.get}")

      val eventProcessingFuture = process(item)

      registerHeartbeat(item)

      eventProcessingFuture
        .flatMap(action => {
          if (stopped) {
            action match {
              case Ack(handle) =>
                logger.info(s"Worker stopped but will still ack $handle")
              case ClearVisibility(handle) =>
                logger.info(
                  s"Worker stopped but will still clear visibility $handle"
                )
              case _ =>
            }
          }

          handleFinished(action)
        })
        .map(_ => {})
        .recover(errorHandler)
        .andThen {
          case _ =>
            unregisterHeartbeat(item.receipt_handle.get)

            outstanding.decrementAndGet()
        }
    } catch {
      case ex: Exception =>
        // always make sure to clean ourselves ups
        logger.error("Exception leaked into throughput worker loop!", ex)

        unregisterHeartbeat(item.receipt_handle.get)

        errorHandler(ex)

        outstanding.decrementAndGet()

        Future.failed(ex)
    }
  }

  /**
    * Blocks until all outstanding messages are flushed (either acked or failed)
    */
  override def flush(): Future[Unit] = {
    if (outstanding.get() == 0) {
      return Future.successful({})
    }

    logger.info(
      s"There are ${outstanding.get()} messages left at shutdown. Attempting to flush."
    )

    val latch = new CountDownLatch(outstanding.get())

    val flusher = Future {
      var last = outstanding.get()
      while (last > 0) {
        val curr = outstanding.get()
        val diff = last - curr
        last = curr
        (0 until diff).foreach(_ => latch.countDown())
        sleep(50 millis)
      }
    }

    val timer = Future {
      latch.await()

      heartbeatRegistry.elements().asScala.foreach(_.complete())

      heartbeatPool.shutdown()

      heartbeatRegistry.clear()
    }

    Future.firstCompletedOf(List(flusher, timer)).map(_ => {})
  }

  private def handleFinished(finishedAction: FinishedAction): Future[Unit] =
    finishedAction match {
      case SqsQueueWorkerBase.Ack(handle) =>
        ackAsync(handle :: Nil)
      case SqsQueueWorkerBase.ClearVisibility(handle) =>
        changeVisibilityAsync(handle :: Nil, None)
      case SqsQueueWorkerBase.DoNothing => Future.unit
    }
}

class SqsQueueThroughputWorkerConfig(
  override val sleepDurationBetweenFailures: Duration = 30 seconds,
  override val sleepDurationBetweenEmptyBatches: Duration = 1 second,
  val sleepDurationWhenQueueFull: Duration = 1 second,
  val maxOutstandingItems: Int = 10,
  override val heartbeat: Option[HeartbeatConfig] = None)
    extends SqsQueueWorkerConfig(
      batchSize = maxOutstandingItems,
      sleepDurationBetweenFailures = sleepDurationBetweenFailures,
      sleepDurationBetweenEmptyBatches = sleepDurationBetweenEmptyBatches,
      heartbeat = heartbeat
    ) {

  override def toString =
    s"SqsQueueThroughputWorkerConfig(sleepDurationBetweenFailures=$sleepDurationBetweenFailures, sleepDurationBetweenEmptyBatches=$sleepDurationBetweenEmptyBatches, sleepDurationWhenQueueFull=$sleepDurationWhenQueueFull, maxOutstandingItems=$maxOutstandingItems, heartbeat=$heartbeat)"
}
