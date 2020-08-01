package com.teletracker.consumers.crawlers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.inject.QueueConfigAnnotations
import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.pubsub.{
  QueueReader,
  SpecificScrapeItemIngestMessage,
  TransparentEventBase
}
import com.teletracker.consumers.impl.{
  ScrapeItemWriterWorker,
  ScrapedItemWriterWorkerConfig
}
import com.teletracker.tasks.util.SourceWriter
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AmazonItemWriterWorker @Inject()(
  sourceWriter: SourceWriter,
  queueReader: QueueReader[SpecificScrapeItemIngestMessage[AmazonItem]],
  config: ReloadableConfig[TeletrackerConfig],
  @QueueConfigAnnotations.AmazonItemWriterConfig workerConfig: ReloadableConfig[
    ScrapedItemWriterWorkerConfig
  ]
)(implicit executionContext: ExecutionContext)
    extends ScrapeItemWriterWorker[AmazonItem](
      sourceWriter,
      queueReader,
      config,
      workerConfig
    )
