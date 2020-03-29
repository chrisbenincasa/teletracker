package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.pubsub.{TaskTag, TeletrackerTaskQueueMessage}
import com.teletracker.consumers.{JobPool, TeletrackerTaskRunnable}
import com.teletracker.consumers.config.ConsumerConfig
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig
}
import com.teletracker.common.tasks.Args
import com.teletracker.common.tasks.TeletrackerTask.FailureResult
import com.teletracker.tasks.TeletrackerTaskRunner
import io.circe.Json
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class TaskQueueWorker(
  queue: SqsQueue[TeletrackerTaskQueueMessage],
  dlq: Option[SqsQueue[TeletrackerTaskQueueMessage]],
  config: SqsQueueThroughputWorkerConfig,
  taskRunner: TeletrackerTaskRunner,
  consumerConfig: ConsumerConfig
)(implicit executionContext: ExecutionContext)
    extends SqsQueueThroughputWorker[TeletrackerTaskQueueMessage](queue, config) {

  private val needsTmdbPool =
    new JobPool("TmdbJobs", consumerConfig.max_tmdb_concurrent_jobs)

  private val normalPool =
    new JobPool("NormalJobs", consumerConfig.max_regular_concurrent_jobs)

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
    }
  }))

  def getUnexecutedTasks: Iterable[TeletrackerTaskQueueMessage] = {
    (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
  }

  override protected def process(
    message: TeletrackerTaskQueueMessage
  ): Future[Option[String]] = {
    try {
      val task = taskRunner.getInstance(message.clazz)
      val completionPromise = Promise[Option[String]]
      val runnable =
        new TeletrackerTaskRunnable(
          message,
          task,
          Args.extractArgs(message.args)
        )

      runnable.addCallback {
        // Do not ack the message if it's retryable
        case (task, FailureResult(NonFatal(error))) if task.retryable =>
          completionPromise.tryFailure(error)
        // Send non-retryables to the DLQ if there is one and ack
        case (_, FailureResult(_)) =>
          dlq.foreach(queue => queue.queue(message))
          completionPromise.success(message.receiptHandle)
        // Ack everything else
        case _ => completionPromise.success(message.receiptHandle)
      }

      logger.info(s"Attempting to schedule ${message.clazz}")

      val submitted =
        if (message.jobTags
              .getOrElse(Set.empty)
              .contains(TaskTag.RequiresTmdbApi)) {
          needsTmdbPool.submit(runnable)
        } else {
          normalPool.submit(runnable)
        }

      if (!submitted) {
        Future.successful(None)
      } else {
        completionPromise.future
      }
    } catch {
      case NonFatal(e) =>
        logger.error(
          s"Unexpected error while handling message: ${message.toString}",
          e
        )

        Future.failed(e)
    }
  }
}
