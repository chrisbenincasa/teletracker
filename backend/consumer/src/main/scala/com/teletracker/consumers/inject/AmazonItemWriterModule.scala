package com.teletracker.consumers.inject

import com.google.inject.Provides
import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.inject.QueueConfigAnnotations
import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.pubsub.{
  QueueReader,
  SpecificScrapeItemIngestMessage,
  TransparentEventBase
}
import com.teletracker.consumers.config.ConsumerConfig
import com.teletracker.consumers.impl.ScrapedItemWriterWorkerConfig
import com.twitter.inject.TwitterModule
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import scala.concurrent.ExecutionContext

class AmazonItemWriterModule extends TwitterModule {
  @Provides
  @QueueConfigAnnotations.AmazonItemWriterConfig
  def taskMessageConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[ScrapedItemWriterWorkerConfig] = {
    consumerConfig.map(conf => {
      new ScrapedItemWriterWorkerConfig(
        conf.amazon_item_worker.batch_size,
        conf.amazon_item_worker.output_prefix
      )
    })
  }

  @Provides
  def queue(
    config: ReloadableConfig[TeletrackerConfig],
    sqsAsyncClient: SqsAsyncClient
  )(implicit executionContext: ExecutionContext
  ): QueueReader[SpecificScrapeItemIngestMessage[AmazonItem]] = {
    new SqsQueue(
      sqsAsyncClient,
      config.currentValue().async.amazonItemQueue.url,
      config
        .currentValue()
        .async
        .amazonItemQueue
        .dlq
        .map(
          dlq =>
            new SqsQueue[SpecificScrapeItemIngestMessage[AmazonItem]](
              sqsAsyncClient,
              dlq.url,
              None
            )
        )
    )
  }
}
