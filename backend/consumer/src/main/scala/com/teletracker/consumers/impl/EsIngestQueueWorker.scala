package com.teletracker.consumers.impl

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.aws.sqs.worker.SqsQueueWorkerBase.FutureOption
import com.teletracker.common.aws.sqs.worker.{
  SqsQueueThroughputWorker,
  SqsQueueThroughputWorkerConfig
}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{
  EsExternalId,
  EsItem,
  EsPerson
}
import com.teletracker.common.elasticsearch.{ItemUpdater, PersonUpdater}
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsIngestIndex,
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestUpdate
}
import com.teletracker.common.inject.QueueConfigAnnotations.EsIngestQueueConfig
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EsIngestQueueWorker @Inject()(
  queue: SqsFifoQueue[EsIngestMessage],
  @EsIngestQueueConfig config: SqsQueueThroughputWorkerConfig,
  itemUpdater: ItemUpdater,
  personUpdater: PersonUpdater,
  teletrackerConfig: TeletrackerConfig,
  denormQueue: SqsFifoQueue[EsDenormalizeItemMessage],
  mappingStore: ElasticsearchExternalIdMappingStore
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

      case EsIngestMessageOperation.Index =>
        msg.index
          .map(handleIndex)
          .getOrElse(Future.unit)
          .map(_ => msg.receiptHandle)
          .recover {
            case NonFatal(e) =>
              logger.error("Encountered error while processing", e)
              None
          }
    }
  }

  private def handleIndex(index: EsIngestIndex) = {
    require(
      isValidIndex(index.index),
      s"Invalid index found: ${index.index} when attempting to index item: ${index.id}"
    )

    if (teletrackerConfig.elasticsearch.items_index_name == index.index) {
      index.doc.as[EsItem] match {
        case Left(value) =>
          logger.error("Could not parse EsItem from json", value)
          Future.unit

        case Right(value) =>
          itemUpdater.insert(value).flatMap {
            case _ if index.externalIdMappings.exists(_.nonEmpty) =>
              val ids =
                index.externalIdMappings
                  .getOrElse(Set.empty)
                  .toList
                  .map(
                    mapping => {
                      (
                        EsExternalId(mapping.source, mapping.id),
                        mapping.itemType
                      ) -> value.id
                    }
                  )
                  .toMap

              mappingStore.mapExternalIds(ids)

            case _ => Future.unit
          }
      }
    } else if (teletrackerConfig.elasticsearch.people_index_name == index.index) {
      index.doc.as[EsPerson] match {
        case Left(value) =>
          logger.error("Could not parse EsItem from json", value)
          Future.unit

        case Right(value) =>
          personUpdater.insert(value).flatMap {
            case _ if index.externalIdMappings.exists(_.nonEmpty) =>
              val ids =
                index.externalIdMappings
                  .getOrElse(Set.empty)
                  .toList
                  .map(
                    mapping => {
                      (
                        EsExternalId(mapping.source, mapping.id),
                        mapping.itemType
                      ) -> value.id
                    }
                  )
                  .toMap

              mappingStore.mapExternalIds(ids)

            case _ => Future.unit
          }
      }
    } else {
      Future.failed(
        new IllegalArgumentException(s"Unexpected index: ${index.index}")
      )
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
      handleItemUpdate(update).flatMap {
        case _ if update.itemDenorm.exists(_.needsDenorm) =>
          denormQueue
            .queue(
              EsDenormalizeItemMessage(
                itemId = UUID.fromString(update.id),
                creditsChanged = update.itemDenorm.get.cast,
                crewChanged = update.itemDenorm.get.crew,
                dryRun = false
              )
            )
            .map(_ => {})
        case _ => Future.unit
      }
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
