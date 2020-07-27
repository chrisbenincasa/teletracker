//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.common.aws.sqs.worker

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.{
  ClearVisibility,
  FinishedAction
}
import com.teletracker.common.pubsub.{EventBase, QueueReader}
import com.teletracker.common.util.execution.ExecutionContextProvider
import com.teletracker.common.aws.sqs.worker.poll.Heartbeats
import com.teletracker.common.config.core.api.ReloadableConfig
import java.util.concurrent.{Executors, Semaphore}
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object SqsQueueBatchWorker {
  def apply[T <: EventBase: Manifest](
    queue: QueueReader[T],
    config: ReloadableConfig[SqsQueueWorkerConfig]
  )(
    processFunc: Seq[T] => Seq[FinishedAction]
  )(implicit
    executionContext: ExecutionContext
  ): SqsQueueBatchWorker[T] = {

    new SqsQueueBatchWorker(queue, config) {
      override protected def process(msg: Seq[T]): Seq[FinishedAction] =
        processFunc(msg)
    }
  }
}

abstract class SqsQueueBatchWorker[T <: EventBase: Manifest](
  protected val queue: QueueReader[T],
  config: ReloadableConfig[SqsQueueWorkerConfig]
)(implicit
  executionContext: ExecutionContext)
    extends SqsQueueWorkerBase[T](queue, config)
    with Heartbeats[T] {

  final override type Wrapper[A] = Seq[A]
  final override type ReturnWrapper[A] = A

  override protected def runInternal(): Unit = runInternalFinal()

  private val batchSemaphore = new Semaphore(1)

  override protected def getConfig: ReloadableConfig[SqsQueueWorkerConfig] =
    config

  protected lazy val heartbeatPool = ExecutionContextProvider.provider.of(
    Executors.newScheduledThreadPool(
      config.currentValue().batchSize
    )
  )

  @tailrec
  private def runInternalFinal(): Unit = {
    if (stopped) {
      return
    }

    try {
      batchSemaphore.acquire()

      val items = dequeue()

      if (items.isEmpty) {
        logger.debug("No items found. Taking a nap...")
        sleepOnEmpty()
      } else {
        items.foreach(registerHeartbeat)

        val actions = process(items)

        val acks = actions.collect {
          case SqsQueueWorkerBase.Ack(handle) => handle
        }.toList

        val clears = actions.collect {
          case ClearVisibility(handle) => handle
        }

        (acks ++ clears).foreach(unregisterHeartbeat)

        ack(acks)
        changeVisibility(clears.toList, duration = None)
      }
    } catch {
      errorHandler andThen { _ =>
        clearAllHeartbeats()
      }
    } finally {
      batchSemaphore.release()
    }

    runInternalFinal()
  }

  override protected def flush(): Future[Unit] = {
    Future {
      batchSemaphore.acquire()
    }
  }
}
