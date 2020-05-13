package com.teletracker.common.aws.sqs

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.storage.{
  TaskRecord,
  TaskRecordCreator,
  TaskRecordStore
}
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SqsTaskScheduler @Inject()(
  sqsAsyncClient: SqsAsyncClient,
  teletrackerConfig: TeletrackerConfig,
  taskRecordCreator: TaskRecordCreator,
  taskRecordStore: TaskRecordStore
)(implicit executionContext: ExecutionContext)
    extends TaskScheduler {

  private val queue =
    new SqsBoundedQueue[TeletrackerTaskQueueMessage](
      sqsAsyncClient,
      teletrackerConfig.async.taskQueue.url
    )

  override def schedule(
    teletrackerTaskQueueMessage: TeletrackerTaskQueueMessage
  ): Future[Unit] = {
    schedule(List(teletrackerTaskQueueMessage))
  }

  override def schedule(
    teletrackerTaskQueueMessage: List[TeletrackerTaskQueueMessage]
  ): Future[Unit] = {
    val taskRecords = teletrackerTaskQueueMessage
      .filter(_.id.isDefined)
      .map(message => {
        taskRecordCreator
          .createScheduled(message.id.get, message.clazz, message.args)
      })

    Future
      .sequence(taskRecords.map(taskRecordStore.recordNewTask))
      .flatMap(_ => {
        queue.batchQueue(
          teletrackerTaskQueueMessage,
          Some(UUID.randomUUID().toString)
        )
      })

  }.map(_ => {})
}
