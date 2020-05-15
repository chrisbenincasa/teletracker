package com.teletracker.consumers.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.aws.sqs.{SqsFifoQueue, SqsQueue}
import com.teletracker.common.aws.sqs.worker.SqsQueueThroughputWorkerConfig
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.config.{ConfigLoader, TeletrackerConfig}
import com.teletracker.common.inject.{Modules => CommonModules}
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsIngestMessage,
  TeletrackerTaskQueueMessage
}
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.consumers.config.ConsumerConfig
import com.twitter.inject.TwitterModule
import net.codingwell.scalaguice.ScalaOptionBinder
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    CommonModules() ++ Seq(
      new ConsumerConfigModule(),
      new ConsumerModule
    )
  }
}

class ConsumerConfigModule extends TwitterModule {
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  @Provides
  @Singleton
  def config: ConsumerConfig = {
    new ConfigLoader().load[ConsumerConfig]("teletracker.consumer")
  }
}

class ConsumerModule extends TwitterModule {
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
