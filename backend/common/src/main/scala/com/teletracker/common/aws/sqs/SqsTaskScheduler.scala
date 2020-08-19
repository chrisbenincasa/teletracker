package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.storage.{TaskRecordCreator, TaskRecordStore}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class SqsTaskScheduler @Inject()(
  taskRecordCreator: TaskRecordCreator,
  taskRecordStore: TaskRecordStore,
  taskQueue: SqsFifoQueue[TeletrackerTaskQueueMessage]
)(implicit executionContext: ExecutionContext)
    extends TaskScheduler {
  private val logger = LoggerFactory.getLogger(getClass)

  override def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage,
    groupId: Option[String] = None
  ): Future[Unit] = {
    schedule(List(teletrackerTaskQueueMessage -> groupId))
  }

  override def schedule(
    teletrackerTaskQueueMessage: List[
      (TeletrackerTaskQueueMessage, Option[String])
    ]
  ): Future[Unit] = {
    val taskRecords = teletrackerTaskQueueMessage
      .map {
        case (message, _) if message.id.isDefined => message
        case (message, _)                         => message.copy(id = Some(UUID.randomUUID()))
      }
      .map(message => {
        taskRecordCreator
          .createScheduled(
            message.id.get,
            message.clazz,
            message.args,
            message.triggerJob
          )
      })

    taskRecordStore
      .recordNewTasks(taskRecords)
      .recover {
        case NonFatal(e) =>
          logger.warn("Could not record tasks", e)
      }
      .flatMap(_ => {
        taskQueue.batchQueue(
          teletrackerTaskQueueMessage.map {
            case (message, group) =>
              message -> group.getOrElse(UUID.randomUUID().toString)
          }
        )
      })

  }.map(_ => {})
}
