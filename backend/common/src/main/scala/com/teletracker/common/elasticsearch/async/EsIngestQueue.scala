package com.teletracker.common.elasticsearch.async

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.async.EsIngestQueue.AsyncItemUpdateRequest
import com.teletracker.common.elasticsearch.model.{EsItem, EsPerson}
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsIngestIndex,
  EsIngestItemDenormArgs,
  EsIngestItemExternalIdMapping,
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestPersonDenormArgs,
  EsIngestUpdate
}
import io.circe.Json
import javax.inject.Inject
import java.util.UUID
import io.circe.syntax._
import scala.concurrent.{ExecutionContext, Future}

class EsIngestQueue @Inject()(
  queue: SqsFifoQueue[EsIngestMessage],
  denormQueue: SqsFifoQueue[EsDenormalizeItemMessage],
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext) {

  def queueItemInsert(esItem: EsItem): Future[Option[EsIngestMessage]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Index,
        index = Some(
          EsIngestIndex(
            index = teletrackerConfig.elasticsearch.items_index_name,
            id = esItem.id.toString,
            externalIdMappings = Some(esItem.externalIdsGrouped.map {
              case (source, str) =>
                EsIngestItemExternalIdMapping(source, str, esItem.`type`)
            }.toSet),
            doc = esItem.asJson
          )
        )
      ),
      messageGroupId = esItem.id.toString
    )
  }

  def queueItemUpdate(
    id: UUID,
    itemType: ItemType,
    doc: Json,
    denorm: Option[EsIngestItemDenormArgs]
  ): Future[Option[EsIngestMessage]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          EsIngestUpdate(
            index = teletrackerConfig.elasticsearch.items_index_name,
            id = id.toString,
            itemType = Some(itemType),
            doc = Some(doc),
            script = None,
            itemDenorm = denorm
          )
        )
      ),
      messageGroupId = id.toString // Handle updates for this item in order
    )
  }

  def queueItemUpdate(
    request: AsyncItemUpdateRequest
  ): Future[Option[EsIngestMessage]] = {
    queueItemUpdates(request :: Nil).map(_.headOption)
  }

  def queueItemUpdates(
    requests: List[AsyncItemUpdateRequest]
  ): Future[List[EsIngestMessage]] = {
    require(requests.forall(r => r.doc.isDefined ^ r.script.isDefined))

    val messages = requests.map(request => {
      EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          EsIngestUpdate(
            index = teletrackerConfig.elasticsearch.items_index_name,
            id = request.id.toString,
            itemType = Some(request.itemType),
            doc = request.doc,
            script = None,
            itemDenorm = request.denorm
          )
        )
      ) -> request.id.toString
    })

    queue.batchQueue(messages)
  }

  def queueItemDenormalization(
    id: UUID,
    denormArgs: EsIngestItemDenormArgs
  ): Future[Unit] = {
    denormQueue
      .queue(
        EsDenormalizeItemMessage(
          itemId = id,
          creditsChanged = denormArgs.cast,
          crewChanged = denormArgs.crew,
          dryRun = false
        ),
        messageGroupId = id.toString
      )
      .map(_ => {})
  }

  def queuePersonInsert(esPerson: EsPerson): Future[Option[EsIngestMessage]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Index,
        index = Some(
          EsIngestIndex(
            index = teletrackerConfig.elasticsearch.people_index_name,
            id = esPerson.id.toString,
            externalIdMappings = Some(esPerson.externalIdsGrouped.map {
              case (source, str) =>
                EsIngestItemExternalIdMapping(source, str, ItemType.Person)
            }.toSet),
            doc = esPerson.asJson
          )
        )
      )
    )
  }

  def queuePersonUpdate(
    id: UUID,
    doc: Json,
    denorm: Option[EsIngestPersonDenormArgs]
  ): Future[Option[EsIngestMessage]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          EsIngestUpdate(
            index = teletrackerConfig.elasticsearch.people_index_name,
            id = id.toString,
            itemType = Some(ItemType.Person),
            doc = Some(doc),
            script = None,
            itemDenorm = None,
            personDenorm = denorm
          )
        )
      ),
      messageGroupId = id.toString // Handle updates for this item in order
    )
  }
}

object EsIngestQueue {
  object AsyncItemUpdateRequest {
    def apply(
      id: UUID,
      itemType: ItemType,
      doc: Json,
      denorm: Option[EsIngestItemDenormArgs]
    ): AsyncItemUpdateRequest = {
      AsyncItemUpdateRequest(id, itemType, Some(doc), None, denorm)
    }

    def script(
      id: UUID,
      itemType: ItemType,
      script: Json,
      denorm: Option[EsIngestItemDenormArgs]
    ): AsyncItemUpdateRequest = {
      AsyncItemUpdateRequest(id, itemType, None, Some(script), denorm)
    }
  }

  case class AsyncItemUpdateRequest(
    id: UUID,
    itemType: ItemType,
    doc: Option[Json],
    script: Option[Json],
    denorm: Option[EsIngestItemDenormArgs])
}
