package com.teletracker.consumers.impl

import com.teletracker.common.pubsub.{JobTags, TeletrackerTaskQueueMessage}
import com.teletracker.consumers.SqsQueue
import com.teletracker.consumers.worker.{
  JobPool,
  SqsQueueBatchWorker,
  SqsQueueWorkerConfig,
  TeletrackerTaskRunnable
}
import com.teletracker.tasks.TeletrackerTaskRunner
import io.circe.Json
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class TaskQueueWorker(
  queue: SqsQueue[TeletrackerTaskQueueMessage],
  config: SqsQueueWorkerConfig,
  taskRunner: TeletrackerTaskRunner
)(implicit executionContext: ExecutionContext)
    extends SqsQueueBatchWorker[TeletrackerTaskQueueMessage](queue, config) {

  private val needsTmdbPool = new JobPool("TmdbJobs", 1)
  private val normalPool = new JobPool("NormalJobs", 2)

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = {
      (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
    }
  }))

  def getUnexecutedTasks: Iterable[TeletrackerTaskQueueMessage] = {
    (needsTmdbPool.getPending ++ normalPool.getPending).map(_.originalMessage)
  }

  override protected def process(
    messages: Seq[TeletrackerTaskQueueMessage]
  ): Seq[String] = {
    messages.flatMap(message => {
      try {
        val task = taskRunner.getInstance(message.clazz)
        val runnable =
          new TeletrackerTaskRunnable(
            message,
            task,
            extractArgs(message.args)
          )

        logger.info(s"Attempting to schedule ${message.clazz}")

        if (message.jobTags
              .getOrElse(Set.empty)
              .contains(JobTags.RequiresTmdbApi)) {
          needsTmdbPool.submit(runnable)
        } else {
          normalPool.submit(runnable)
        }

        message.receipt_handle
      } catch {
        case NonFatal(e) =>
          logger.error(
            s"Unexpected error while handling message: ${message.toString}",
            e
          )

          None
      }
    })
  }

  private def extractArgs(args: Map[String, Json]): Map[String, Option[Any]] = {
    args.mapValues(extractValue)
  }

  private def extractValue(j: Json): Option[Any] = {
    j.fold(
      None,
      Some(_),
      x => Some(x.toDouble),
      Some(_),
      v => Some(v.map(extractValue)),
      o => Some(o.toMap.mapValues(extractValue))
    )
  }
}
