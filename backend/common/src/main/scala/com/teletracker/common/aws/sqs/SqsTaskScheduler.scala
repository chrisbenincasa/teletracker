package com.teletracker.common.aws.sqs

import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.storage.{TaskRecordCreator, TaskRecordStore}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SqsTaskScheduler @Inject()(
  taskRecordCreator: TaskRecordCreator,
  taskRecordStore: TaskRecordStore,
  taskQueue: SqsFifoQueue[TeletrackerTaskQueueMessage]
)(implicit executionContext: ExecutionContext)
    extends TaskScheduler {
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
      .collect {
        case (message, _) if message.id.isDefined => message
      }
      .map(message => {
        taskRecordCreator
          .createScheduled(message.id.get, message.clazz, message.args)
      })

    Future
      .sequence(taskRecords.map(taskRecordStore.recordNewTask))
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
