package com.teletracker.common.aws.sqs

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.{
  TaskScheduler,
  TeletrackerTaskQueueMessage
}
import javax.inject.Inject
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SqsTaskScheduler @Inject()(
  sqsAsyncClient: SqsAsyncClient,
  teletrackerConfig: TeletrackerConfig
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
    queue
      .queue(
        teletrackerTaskQueueMessage,
        Some(UUID.randomUUID().toString)
      )
      .map(_ => {})
  }

  override def schedule(
    teletrackerTaskQueueMessage: List[TeletrackerTaskQueueMessage]
  ): Future[Unit] = {
    queue.batchQueue(
      teletrackerTaskQueueMessage,
      Some(UUID.randomUUID().toString)
    )
  }.map(_ => {})
}
