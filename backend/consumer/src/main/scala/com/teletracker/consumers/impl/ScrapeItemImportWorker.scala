package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig,
  SqsQueueWorkerBase
}
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.db.dynamo.{DynamoScrapedItem, ScrapeItemStore}
import com.teletracker.common.elasticsearch.scraping.{
  EsScrapedItemDoc,
  EsScrapedItemStore
}
import com.teletracker.common.inject.QueueConfigAnnotations.ScrapeItemQueueConfig
import com.teletracker.common.pubsub.ScrapeItemIngestMessage
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ScrapeItemImportWorker @Inject()(
  queue: SqsQueue[ScrapeItemIngestMessage],
  @ScrapeItemQueueConfig
  config: ReloadableConfig[SqsQueueThroughputWorkerConfig],
  esScrapedItemStore: EsScrapedItemStore,
  scrapeItemStore: ScrapeItemStore
)(implicit executionContext: ExecutionContext)
    extends SqsQueueThroughputWorker[ScrapeItemIngestMessage](queue, config) {
  override protected def process(
    msg: ScrapeItemIngestMessage
  ): Future[SqsQueueWorkerBase.FinishedAction] = {
    msg.deserToScrapedItem match {
      case Success(value) =>
        handleForEsScrapedItem(
          EsScrapedItemDoc
            .fromAnyScrapedItem(msg.`type`, msg.version, value, msg.item),
          msg
        )

      case Failure(exception) =>
        logger.error("Could not deserialize item, unsupported type", exception)
        Future.successful(SqsQueueWorkerBase.Ack(msg.receiptHandle.get))
    }
  }

  private def handleForEsScrapedItem(
    esScrapedItem: EsScrapedItemDoc,
    msg: ScrapeItemIngestMessage
  ): Future[SqsQueueWorkerBase.FinishedAction] = {
    scrapeItemStore
      .put(DynamoScrapedItem.fromEsScrapedItemDoc(esScrapedItem))
      .transformWith {
        case Failure(exception) =>
          logger.error(
            s"Error while saving item to Dynamo (id=${esScrapedItem.id}). Retrying.",
            exception
          )
          Future.successful(SqsQueueWorkerBase.DoNothing)

        case Success(_) =>
          esScrapedItemStore
            .index(esScrapedItem)
            .map(_ => {})
            .recover {
              case NonFatal(e) =>
                logger.error(
                  s"Error while indexing item (id=${esScrapedItem.id}). Retrying.",
                  e
                )

                SqsQueueWorkerBase.DoNothing
            }
            .map(_ => {
              SqsQueueWorkerBase.Ack(msg.receiptHandle.get)
            })
      }
  }
}
