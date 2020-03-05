//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.common.aws.sqs.worker

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.pubsub.EventBase
import com.teletracker.common.util.execution.ExecutionContextProvider
import com.teletracker.common.aws.sqs.worker.poll.Heartbeats
import java.util.concurrent.{Executors, Semaphore}
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
  protected val queue: SqsQueue[T],
  config: SqsQueueWorkerConfig
)(implicit
  executionContext: ExecutionContext)
    extends SqsQueueWorkerBase[T, Seq, SqsQueueWorkerBase.Id](queue, config)
    with Heartbeats[T] {

  override protected def runInternal(): Unit = runInternalFinal()

  private val batchSemaphore = new Semaphore(1)

  override protected def getConfig: SqsQueueWorkerConfig = config

  protected lazy val heartbeatPool = ExecutionContextProvider.provider.of(
    Executors.newScheduledThreadPool(
      config.batchSize
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

        val handlesToRemove = process(items)

        handlesToRemove.foreach(unregisterHeartbeat)

        ack(handlesToRemove.toList)
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
