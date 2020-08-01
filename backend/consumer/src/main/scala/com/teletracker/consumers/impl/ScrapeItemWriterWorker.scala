package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.Ack
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueAsyncBatchWorker,
  SqsQueueWorkerBase,
  SqsQueueWorkerConfig
}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.pubsub.{
  QueueReader,
  SpecificScrapeItemIngestMessage,
  TransparentEventBase
}
import com.teletracker.tasks.util.SourceWriter
import io.circe.{Codec, Encoder}
import io.circe.syntax._
import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import java.net.URI
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ScrapedItemWriterWorkerConfig(
  batchSize: Int,
  val outputPrefix: String)
    extends SqsQueueWorkerConfig(batchSize)

abstract class ScrapeItemWriterWorker[T <: ScrapedItem: Codec](
  sourceWriter: SourceWriter,
  queueReader: QueueReader[SpecificScrapeItemIngestMessage[T]],
  config: ReloadableConfig[TeletrackerConfig],
  workerConfig: ReloadableConfig[ScrapedItemWriterWorkerConfig]
)(implicit executionContext: ExecutionContext)
    extends SqsQueueAsyncBatchWorker[SpecificScrapeItemIngestMessage[T]](
      queueReader,
      workerConfig
    ) {

  private val today = LocalDate.now()
  private val now = System.currentTimeMillis()
  private var version: java.lang.Long = -1
  private val outputFile = new File(
    s"${getClass.getSimpleName}-${today}-${now}-items.jl"
  )
  private val writer = new PrintWriter(
    new BufferedWriter(new FileWriter(outputFile))
  )

  override protected def process(
    msg: Seq[SpecificScrapeItemIngestMessage[T]]
  ): Future[Seq[SqsQueueWorkerBase.FinishedAction]] = {
    if (msg.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      version.synchronized {
        if (version == -1) {
          val messageVersion = msg
            .find(m => m.version != -1 && m.version != null)
            .getOrElse(
              throw new IllegalArgumentException(
                "Could not find correct version for messages"
              )
            )

          version = messageVersion.version
        }
      }

      val receipts = msg.flatMap(message => {
        val item = message.item
        writer.println(item.asJson.noSpaces)
        message.receiptHandle
      })

      Future.successful(receipts.map(Ack))
    }
  }

  override def stop(): Future[Unit] = {
    synchronized {

      writer.flush()
      writer.close()
      val bucket = config.currentValue().data.s3_bucket
      val prefix = workerConfig.currentValue().outputPrefix
      val fullPath = s"s3://$bucket/$prefix/$today/items_${version}.jl"

      logger.info(s"Uploading file to: $fullPath")

      sourceWriter.writeFile(
        URI.create(fullPath),
        outputFile.toPath
      )
    }

    super.stop()
  }
}
