//
// Copyright (c) 2011-2017 by Curalate, Inc.
//

package com.teletracker.common.aws.sqs.worker

import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.{
  Ack,
  ClearVisibility,
  FinishedAction
}
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.pubsub.{EventBase, QueueReader}
import java.util.concurrent.Semaphore
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object SqsQueueAsyncBatchWorker {
  def apply[T <: EventBase: Manifest](
    queue: QueueReader[T],
    config: ReloadableConfig[SqsQueueWorkerConfig]
  )(
    processFunc: Seq[T] => Future[Seq[FinishedAction]]
  )(implicit
    executionContext: ExecutionContext
  ): SqsQueueAsyncBatchWorker[T] = {

    new SqsQueueAsyncBatchWorker(queue, config) {
      override protected def process(msg: Seq[T]): Future[Seq[FinishedAction]] =
        processFunc(msg)
    }
  }
}

abstract class SqsQueueAsyncBatchWorker[T <: EventBase: Manifest](
  queue: QueueReader[T],
  config: ReloadableConfig[SqsQueueWorkerConfig]
)(implicit
  executionContext: ExecutionContext)
    extends SqsQueueWorkerBase[T](queue, config) {

  final override type Wrapper[A] = Seq[A]
  final override type ReturnWrapper[A] = Future[A]

  override protected def runInternal(): Unit = runInternalFinal()

  private val batchSemaphore = new Semaphore(1)

  @tailrec
  private def runInternalFinal(): Unit = {
    if (stopped) {
      return
    }

    // Attempt to acquire the lock for a batch
    if (batchSemaphore.tryAcquire()) {
      logger.debug("Semaphore acquired!")

      val items = dequeue()

      // If items are empty, release the lock and sleep for a moment
      if (items.isEmpty) {
        logger.debug("No items found. Taking a nap...")
        batchSemaphore.release()
        sleepOnEmpty()
      } else {
        try {
          // Otherwise, run the item batch and release the lock when all items have finished
          // regardless of whether they succeeded or not
          process(items)
            .flatMap(handleProcessFinished)
            .recover {
              case CanHandleError(ex) => errorHandler(ex)
              case ex => {
                logger.error(
                  s"Encountered non-recoverable error: ${ex.getMessage}",
                  ex
                )
                throw ex
              }
            }
            .andThen {
              case _ =>
                logger.debug("Releasing semaphore")
                batchSemaphore.release()
            }
        } catch {
          // If somehow an exception leaks out of the Future context, we handle it here by
          // either invoking the error handler (if the error is handleable) or applying a standard
          // sleep before unlocking the batch lock and recursing. We assume that an exception
          // that leaks out of the Future context and into the worker thread will never hit the lock
          // release within the Future callback above
          case NonFatal(ex) =>
            logger.error("Exception leaked into batch worker loop!", ex)

            ex match {
              case CanHandleError(e) => errorHandler(e)
              case _                 => sleepOnFail()
            }

            batchSemaphore.release()
        }
      }
    } else {
      // Sleep for a moment and attempt to grab the lock again
      logger.debug("Could not acquire batch processing lock")
      sleepOnEmpty()
    }

    runInternalFinal()
  }

  private def handleProcessFinished(
    actions: Seq[FinishedAction]
  ): Future[Unit] = {
    val acks = actions.collect {
      case Ack(handle) => handle
    }.toList

    val clears = actions.collect {
      case ClearVisibility(handle) => handle
    }.toList

    val ackFut = ackAsync(acks)
    val changeFut = changeVisibilityAsync(clears, None)

    for {
      _ <- ackFut
      _ <- changeFut
    } yield {}
  }

  override protected def flush(): Future[Unit] = {
    Future {
      batchSemaphore.acquire()
    }
  }
}
