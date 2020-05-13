package com.teletracker.consumers.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.aws.sqs.{SqsFifoQueue, SqsQueue}
import com.teletracker.common.aws.sqs.worker.SqsQueueThroughputWorkerConfig
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.config.{ConfigLoader, TeletrackerConfig}
import com.teletracker.common.inject.{Modules => CommonModules}
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
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
          defaultGroupId = TaskMessageHelper.MessageGroupId
        )
      }),
      TaskMessageHelper.MessageGroupId
    )

  @Provides
  @TaskConsumerQueueConfig
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
}
