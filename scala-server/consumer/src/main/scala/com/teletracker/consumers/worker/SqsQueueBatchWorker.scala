//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.consumers.worker

import com.teletracker.common.pubsub.EventBase
import com.teletracker.consumers.SqsQueue
import java.util.concurrent.Semaphore
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object SqsQueueBatchWorker {
  def apply[T <: EventBase: Manifest](
    queue: SqsQueue[T],
    config: SqsQueueWorkerConfig
  )(
    processFunc: Seq[T] => Seq[String]
  )(implicit
    executionContext: ExecutionContext
  ): SqsQueueBatchWorker[T] = {

    new SqsQueueBatchWorker(queue, config) {
      override protected def process(msg: Seq[T]): Seq[String] =
        processFunc(msg)
    }
  }
}

abstract class SqsQueueBatchWorker[T <: EventBase: Manifest](
  queue: SqsQueue[T],
  config: SqsQueueWorkerConfig
)(implicit
  executionContext: ExecutionContext)
    extends SqsQueueWorkerBase[T, Seq, SqsQueueWorkerBase.Id](queue, config) {

  override protected def runInternal(): Unit = runInternalFinal()

  private val batchSemaphore = new Semaphore(1)

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
        val handlesToRemove = process(items)

        ack(handlesToRemove.toList)
      }
    } catch {
      errorHandler
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
