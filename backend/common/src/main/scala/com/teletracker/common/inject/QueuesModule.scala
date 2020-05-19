package com.teletracker.common.inject

import com.google.inject.Provides
import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueThroughputWorkerConfig
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsIngestMessage,
  TeletrackerTaskQueueMessage
}
import com.twitter.inject.TwitterModule
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class QueuesModule extends TwitterModule {
  @Provides
  def taskMessageQueue(
    config: TeletrackerConfig,
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): SqsFifoQueue[TeletrackerTaskQueueMessage] =
    new SqsFifoQueue[TeletrackerTaskQueueMessage](
      sqsAsyncClient,
      config.async.taskQueue.url,
      config.async.taskQueue.dlq.map(dlqConf => {
        new SqsFifoQueue[TeletrackerTaskQueueMessage](
          sqsAsyncClient,
          dlqConf.url,
          defaultGroupId = dlqConf.message_group_id.getOrElse("default")
        )
      }),
      config.async.taskQueue.message_group_id.getOrElse("default")
    )

  @Provides
  @QueueConfigAnnotations.TaskConsumerQueueConfig
  def taskMessageConfig = {
    new SqsQueueThroughputWorkerConfig(
      maxOutstandingItems = 2,
      heartbeat = Some(
        HeartbeatConfig(
          heartbeat_frequency = 15 seconds,
          visibility_timeout = 5 minutes
        )
      )
    )
  }

  @Provides
  def esIngestQueue(
    config: TeletrackerConfig,
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): SqsFifoQueue[EsIngestMessage] =
    new SqsFifoQueue[EsIngestMessage](
      sqsAsyncClient,
      config.async.esIngestQueue.url,
      config.async.esIngestQueue.dlq.map(dlqConf => {
        new SqsFifoQueue[EsIngestMessage](
          sqsAsyncClient,
          dlqConf.url,
          defaultGroupId = dlqConf.message_group_id.getOrElse("default")
        )
      }),
      config.async.esIngestQueue.message_group_id.getOrElse("default")
    )

  @Provides
  @QueueConfigAnnotations.EsIngestQueueConfig
  def esIngestConfig = {
    new SqsQueueThroughputWorkerConfig(
      maxOutstandingItems = 1,
      heartbeat = Some(
        HeartbeatConfig(
          heartbeat_frequency = 15 seconds,
          visibility_timeout = 1 minutes
        )
      )
    )
  }

  @Provides
  def esItemDenormQueue(
    config: TeletrackerConfig,
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): SqsFifoQueue[EsDenormalizeItemMessage] =
    new SqsFifoQueue[EsDenormalizeItemMessage](
      sqsAsyncClient,
      config.async.esItemDenormalizationQueue.url,
      config.async.esItemDenormalizationQueue.dlq.map(dlqConf => {
        new SqsFifoQueue[EsDenormalizeItemMessage](
          sqsAsyncClient,
          dlqConf.url,
          defaultGroupId = dlqConf.message_group_id.getOrElse("default")
        )
      }),
      config.async.esItemDenormalizationQueue.message_group_id
        .getOrElse("default")
    )

  @Provides
  @QueueConfigAnnotations.DenormalizeItemQueueConfig
  def esDenormConfig = {
    new SqsQueueThroughputWorkerConfig(
      maxOutstandingItems = 1,
      heartbeat = Some(
        HeartbeatConfig(
          heartbeat_frequency = 15 seconds,
          visibility_timeout = 1 minutes
        )
      )
    )
  }
}
