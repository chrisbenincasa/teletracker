package com.teletracker.tasks.inject

import com.google.inject.{Module, Provides}
import com.teletracker.common.aws.sqs.{SqsFifoQueue, SqsQueue}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.TeletrackerTaskQueueMessage
import com.teletracker.common.tasks.TaskMessageHelper
import com.twitter.inject.TwitterModule
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext

object TaskModules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] =
    Seq(
      new HttpClientModule,
      new FactoriesModule,
      QueueModule
    )
}

object QueueModule extends TwitterModule {
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
          None,
          TaskMessageHelper.MessageGroupId
        )
      }),
      TaskMessageHelper.MessageGroupId
    )
}
