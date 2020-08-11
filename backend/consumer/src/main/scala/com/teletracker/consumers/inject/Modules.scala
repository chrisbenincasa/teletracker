package com.teletracker.consumers.inject

import com.google.inject.{Module, Provides, Singleton}
import com.teletracker.common.aws.sqs.worker.poll.HeartbeatConfig
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorkerConfig,
  SqsQueueWorkerConfig
}
import com.teletracker.common.config.core.ConfigLoader
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.inject.QueueConfigAnnotations.ScrapeItemQueueConfig
import com.teletracker.common.inject.{
  QueueConfigAnnotations,
  Modules => CommonModules
}
import com.teletracker.consumers.config.ConsumerConfig
import com.teletracker.tasks.inject.FactoriesModule
import com.twitter.inject.TwitterModule
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Modules {
  def apply()(implicit executionContext: ExecutionContext): Seq[Module] = {
    CommonModules() ++ Seq(
      new HttpClientModule,
      new ConsumerConfigModule(),
      new FactoriesModule,
      new AmazonItemWriterModule
    )
  }
}

class ConsumerConfigModule extends TwitterModule {
  import com.teletracker.common.config.core.readers.ValueReaders._

  @Provides
  @Singleton
  def config(configLoader: ConfigLoader): ReloadableConfig[ConsumerConfig] = {
    configLoader.loadType(ConsumerConfig)
  }

  @Provides
  @QueueConfigAnnotations.TaskConsumerQueueConfig
  def taskMessageConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[SqsQueueThroughputWorkerConfig] = {
    consumerConfig.map(conf => {
      new SqsQueueThroughputWorkerConfig(
        maxOutstandingItems = conf.max_regular_concurrent_jobs,
        sleepDurationWhenQueueFull = 30 seconds,
        heartbeat = Some(
          HeartbeatConfig(
            heartbeat_frequency = 15 seconds,
            visibility_timeout = 5 minutes
          )
        )
      )
    })
  }

  @Provides
  @QueueConfigAnnotations.EsIngestQueueConfig
  def esIngestConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[SqsQueueThroughputWorkerConfig] = {
    consumerConfig.map(conf => {
      new SqsQueueThroughputWorkerConfig(
        maxOutstandingItems = conf.es_ingest_worker.max_outstanding,
        heartbeat = None
      )
    })
  }

  @Provides
  @QueueConfigAnnotations.DenormalizeItemQueueConfig
  def esDenormConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[SqsQueueWorkerConfig] = {
    consumerConfig.map(conf => {
      new SqsQueueWorkerConfig(
        batchSize = conf.es_item_denorm_worker.batch_size,
        heartbeat = None
      )
    })
  }

  @Provides
  @QueueConfigAnnotations.DenormalizePersonQueueConfig
  def esDenormPersonConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[SqsQueueWorkerConfig] = {
    consumerConfig.map(conf => {
      new SqsQueueWorkerConfig(
        batchSize = conf.es_item_denorm_worker.batch_size,
        heartbeat = None
      )
    })
  }

  @Provides
  @ScrapeItemQueueConfig
  def scrapeItemConfig(
    consumerConfig: ReloadableConfig[ConsumerConfig]
  ): ReloadableConfig[SqsQueueThroughputWorkerConfig] = {
    consumerConfig.map(conf => {
      new SqsQueueThroughputWorkerConfig(
        maxOutstandingItems = conf.scrape_item_worker.max_outstanding
      )
    })
  }
}
