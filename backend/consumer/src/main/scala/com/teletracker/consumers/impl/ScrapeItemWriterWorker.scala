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
import com.teletracker.common.pubsub.{QueueReader, TransparentEventBase}
import com.teletracker.tasks.util.SourceWriter
import io.circe.Encoder
import io.circe.syntax._
import java.io.{BufferedWriter, File, FileWriter, PrintWriter}
import java.net.URI
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ScrapedItemWriterWorkerConfig(
  batchSize: Int,
  val outputPrefix: String)
    extends SqsQueueWorkerConfig(batchSize)

abstract class ScrapeItemWriterWorker[T <: ScrapedItem: Encoder](
  sourceWriter: SourceWriter,
  queueReader: QueueReader[TransparentEventBase[T]],
  config: ReloadableConfig[TeletrackerConfig],
  workerConfig: ReloadableConfig[ScrapedItemWriterWorkerConfig]
)(implicit executionContext: ExecutionContext)
    extends SqsQueueAsyncBatchWorker[TransparentEventBase[T]](
      queueReader,
      workerConfig
    ) {

  private val today = LocalDate.now()
  private val now = System.currentTimeMillis()
  private var version: java.lang.Long = _
  private val outputFile = new File(
    s"${getClass.getSimpleName}-${today}-${now}-items.jl"
  )
  private val writer = new PrintWriter(
    new BufferedWriter(new FileWriter(outputFile))
  )

  override protected def process(
    msg: Seq[TransparentEventBase[T]]
  ): Future[Seq[SqsQueueWorkerBase.FinishedAction]] = {
    if (msg.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      version.synchronized {
        if (version eq null) {
          val messageVersion = msg
            .find(_.underlying.version != -1)
            .getOrElse(
              throw new IllegalArgumentException(
                "Could not find correct version for messages"
              )
            )

          version = messageVersion.underlying.version
        }
      }

      val receipts = msg.flatMap(message => {
        val item = message.underlying
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

      sourceWriter.writeFile(
        URI.create(s"s3://$bucket/$prefix/$today/items_${version}.jl"),
        outputFile.toPath
      )
    }

    super.stop()
  }
}
