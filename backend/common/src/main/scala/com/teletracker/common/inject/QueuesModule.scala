package com.teletracker.common.inject

import com.google.inject.Provides
import com.teletracker.common.aws.sqs.{SqsFifoQueue, SqsQueue}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsDenormalizePersonMessage,
  EsIngestMessage,
  ScrapeItemIngestMessage,
  TeletrackerTaskQueueMessage
}
import com.twitter.inject.TwitterModule
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext

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
  def esPersonDenormQueue(
    config: TeletrackerConfig,
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): SqsFifoQueue[EsDenormalizePersonMessage] =
    new SqsFifoQueue[EsDenormalizePersonMessage](
      sqsAsyncClient,
      config.async.esPersonDenormalizationQueue.url,
      config.async.esPersonDenormalizationQueue.dlq.map(dlqConf => {
        new SqsFifoQueue[EsDenormalizePersonMessage](
          sqsAsyncClient,
          dlqConf.url,
          defaultGroupId = dlqConf.message_group_id.getOrElse("default")
        )
      }),
      config.async.esPersonDenormalizationQueue.message_group_id
        .getOrElse("default")
    )

  @Provides
  def scrapeItemQueue(
    config: TeletrackerConfig,
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): SqsQueue[ScrapeItemIngestMessage] =
    new SqsQueue[ScrapeItemIngestMessage](
      sqsAsyncClient,
      config.async.scrapeItemQueue.url,
      config.async.scrapeItemQueue.dlq.map(
        dlqConf =>
          new SqsQueue[ScrapeItemIngestMessage](
            sqsAsyncClient,
            dlqConf.url,
            None
          )
      )
    )
}
