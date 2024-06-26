package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.{
  Ack,
  ClearVisibility,
  DoNothing,
  FinishedAction
}
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig
}
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.inject.QueueConfigAnnotations
import com.teletracker.common.pubsub.{
  FailedMessage,
  TaskTag,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.TeletrackerTask.FailureResult
import com.teletracker.common.tasks.args.JsonTaskArgs
import com.teletracker.common.tasks.storage.{
  TaskRecord,
  TaskRecordCreator,
  TaskRecordStore,
  TaskStatus
}
import com.teletracker.common.util.Futures._
import com.teletracker.consumers.config.ConsumerConfig
import com.teletracker.consumers.{JobPool, TeletrackerTaskRunnable}
import com.teletracker.tasks.TeletrackerTaskRunner
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class TaskQueueWorker @Inject()(
  queue: SqsFifoQueue[TeletrackerTaskQueueMessage],
  @QueueConfigAnnotations.TaskConsumerQueueConfig
  config: ReloadableConfig[SqsQueueThroughputWorkerConfig],
  taskRunner: TeletrackerTaskRunner,
  consumerConfig: ReloadableConfig[ConsumerConfig],
  taskRecordStore: TaskRecordStore,
  taskRecordCreator: TaskRecordCreator
)(implicit executionContext: ExecutionContext)
    extends SqsQueueThroughputWorker[TeletrackerTaskQueueMessage](queue, config) {
  private val normalPool =
    new JobPool(
      "JobPool",
      () => consumerConfig.currentValue().max_regular_concurrent_jobs
    )

  def getUnexecutedTasks: Iterable[TeletrackerTaskQueueMessage] = {
    normalPool.getPending.map(_.originalMessage)
  }

  def requeueUnfinishedTasks(
  ): Future[List[FailedMessage[TeletrackerTaskQueueMessage]]] = {
    queue.batchQueue(
      getUnexecutedTasks.toList.map(
        message => message -> message.messageGroupId.getOrElse(message.clazz)
      )
    )
  }

  override protected def process(
    message: TeletrackerTaskQueueMessage
  ): Future[FinishedAction] = {
    try {
      val task = taskRunner.getInstance(message.clazz)
      val taskId = message.id.getOrElse(UUID.randomUUID())
      task.taskId = taskId

      val extractedArgs = JsonTaskArgs.extractArgs(message.args)

      val taskRecord = taskRecordCreator.createGen(
        taskId,
        task,
        message.args,
        TaskStatus.Executing,
        message.triggerJob
      )

      try {
        taskRecordStore.setTaskStarted(taskRecord).await()
      } catch {
        case NonFatal(e) =>
          logger.error("Could not update task in store", e)
      }

      val completionPromise = Promise[Option[String]]
      val runnable =
        new TeletrackerTaskRunnable(
          message,
          task,
          extractedArgs
        )

      runnable.addCallback {
        // Do not ack the message if it's retryable
        case (task, FailureResult(NonFatal(error))) if task.retryable =>
          setTaskFailedInStore(taskRecord)
          completionPromise.tryFailure(error)
        // Send non-retryables to the DLQ if there is one and ack
        case (_, FailureResult(_)) =>
          setTaskFailedInStore(taskRecord)
          queue.dlq.foreach(
            dlq =>
              dlq.queue(
                message,
                message.messageGroupId.getOrElse(dlq.defaultGroupId)
              )
          )
          completionPromise.success(message.receiptHandle)
        // Ack everything else
        case _ =>
          setTaskSuccessInStore(taskRecord)
          completionPromise.success(message.receiptHandle)
      }

      logger.info(s"Attempting to schedule ${message.clazz}")

      val submitted = normalPool.submit(runnable)

      if (!submitted) {
        Future.successful(
          message.receiptHandle.map(ClearVisibility).getOrElse(DoNothing)
        )
      } else {
        completionPromise.future.map(_.map(Ack).getOrElse(DoNothing))
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

  override def stop(): Future[Unit] = {
    super
      .stop()
      .flatMap(_ => {
        requeueUnfinishedTasks().map(_ => {})
      })
  }

  private def setTaskSuccessInStore(taskRecord: TaskRecord) = {
    try {
      taskRecordStore
        .setTaskSuccess(
          taskRecord.id
        )
        .await()
    } catch {
      case NonFatal(e) =>
        logger.error("Could not update task in store", e)
    }
  }

  private def setTaskFailedInStore(taskRecord: TaskRecord) = {
    try {
      taskRecordStore
        .setTaskFailed(
          taskRecord.id
        )
        .await()
    } catch {
      case NonFatal(e) =>
        logger.error("Could not update task in store", e)
    }
  }
}
