package com.teletracker.consumers.crawlers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.pubsub.{QueueReader, TransparentEventBase}
import com.teletracker.consumers.impl.{
  ScrapeItemWriterWorker,
  ScrapedItemWriterWorkerConfig
}
import com.teletracker.tasks.util.SourceWriter
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AmazonItemWriterWorker @Inject()(
  sourceWriter: SourceWriter,
  queueReader: QueueReader[TransparentEventBase[AmazonItem]],
  config: ReloadableConfig[TeletrackerConfig],
  workerConfig: ReloadableConfig[ScrapedItemWriterWorkerConfig]
)(implicit executionContext: ExecutionContext)
    extends ScrapeItemWriterWorker[AmazonItem](
      sourceWriter,
      queueReader,
      config,
      workerConfig
    )
