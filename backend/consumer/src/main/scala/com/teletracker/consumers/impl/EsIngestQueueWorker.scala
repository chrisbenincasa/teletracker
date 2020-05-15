package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.FutureOption
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig
}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{ItemUpdater, PersonUpdater}
import com.teletracker.common.pubsub.{
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestUpdate
}
import com.teletracker.consumers.inject.QueueConfigAnnotations.EsIngestQueueConfig
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EsIngestQueueWorker @Inject()(
  queue: SqsFifoQueue[EsIngestMessage],
  @EsIngestQueueConfig config: SqsQueueThroughputWorkerConfig,
  itemUpdater: ItemUpdater,
  personUpdater: PersonUpdater,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends SqsQueueThroughputWorker[EsIngestMessage](queue, config) {
  override protected def process(msg: EsIngestMessage): FutureOption[String] = {
    msg.operation match {
      case EsIngestMessageOperation.Update =>
        msg.update
          .map(handleUpdate)
          .getOrElse(Future.unit)
          .map(_ => msg.receiptHandle)
          .recover {
            case NonFatal(e) =>
              logger.error("Encountered error while processing", e)
              None
          }
    }
  }

  private def handleUpdate(update: EsIngestUpdate) = {
    require(
      isValidIndex(update.index),
      s"Invalid index found: ${update.index} when attempting to update item: ${update.id}"
    )

    require(
      update.script.isDefined ^ update.doc.isDefined,
      s"Either script or doc must be defined, but not both. id = ${update.id}"
    )

    if (teletrackerConfig.elasticsearch.items_index_name == update.index) {
      handleItemUpdate(update)
    } else if (teletrackerConfig.elasticsearch.people_index_name == update.index) {
      handlePersonUpdate(update)
    } else {
      Future.failed(
        new IllegalArgumentException(s"Unexpected index: ${update.index}")
      )
    }
  }

  private def handleItemUpdate(update: EsIngestUpdate) = {
    if (update.doc.isDefined) {
      itemUpdater
        .updateFromJson(
          UUID.fromString(update.id),
          update.itemType,
          update.doc.get
        )
        .map(_ => {})
    } else if (update.script.isDefined) {
      itemUpdater
        .updateWithScript(
          UUID.fromString(update.id),
          update.script.get
        )
        .map(_ => {})
    } else {
      Future.failed(
        new IllegalArgumentException(
          s"Update for item id = ${update.id} index = ${update.index} had no doc or script defined"
        )
      )
    }
  }

  private def handlePersonUpdate(update: EsIngestUpdate) = {
    if (update.doc.isDefined) {
      personUpdater
        .updateFromJson(
          UUID.fromString(update.id),
          update.doc.get
        )
        .map(_ => {})
    } else if (update.script.isDefined) {
      personUpdater
        .updateWithScript(
          UUID.fromString(update.id),
          update.script.get
        )
        .map(_ => {})
    } else {
      logger.error(
        s"Update for item id = ${update.id} index = ${update.index} had no doc or script defined"
      )

      Future.unit
    }
  }

  private def isValidIndex(index: String) = {
    teletrackerConfig.elasticsearch.items_index_name == index ||
    teletrackerConfig.elasticsearch.people_index_name == index
  }
}
