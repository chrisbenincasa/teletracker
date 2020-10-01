package com.teletracker.common.elasticsearch.async

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.async.EsIngestQueue.{
  AsyncItemUpdateRequest,
  AsyncPersonUpdateRequest
}
import com.teletracker.common.elasticsearch.model.{EsItem, EsPerson}
import com.teletracker.common.pubsub.{
  EsDenormalizeItemMessage,
  EsDenormalizePersonMessage,
  EsIngestIndex,
  EsIngestItemDenormArgs,
  EsIngestItemExternalIdMapping,
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestPersonDenormArgs,
  EsIngestUpdate,
  FailedMessage
}
import io.circe.Json
import javax.inject.Inject
import java.util.UUID
import io.circe.syntax._
import scala.concurrent.{ExecutionContext, Future}

class EsIngestQueue @Inject()(
  queue: SqsFifoQueue[EsIngestMessage],
  denormQueue: SqsFifoQueue[EsDenormalizeItemMessage],
  personDenormQueue: SqsFifoQueue[EsDenormalizePersonMessage],
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext) {

  def queueItemInsert(
    esItem: EsItem
  ): Future[Option[FailedMessage[EsIngestMessage]]] = {
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
  ): Future[Option[FailedMessage[EsIngestMessage]]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          EsIngestUpdate(
            index = teletrackerConfig.elasticsearch.items_index_name,
            id = id.toString,
            itemType = itemType,
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
  ): Future[Option[FailedMessage[EsIngestMessage]]] = {
    queueItemUpdates(request :: Nil).map(_.headOption)
  }

  def queueItemUpdates(
    requests: List[AsyncItemUpdateRequest]
  ): Future[List[FailedMessage[EsIngestMessage]]] = {
    require(requests.forall(r => r.doc.isDefined ^ r.script.isDefined))

    val messages = requests.map(request => {
      EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          EsIngestUpdate(
            index = teletrackerConfig.elasticsearch.items_index_name,
            id = request.id.toString,
            itemType = request.itemType,
            doc = request.doc,
            script = request.script,
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

  def queuePersonInsert(
    esPerson: EsPerson
  ): Future[Option[FailedMessage[EsIngestMessage]]] = {
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
  ): Future[Option[FailedMessage[EsIngestMessage]]] = {
    queue.queue(
      message = EsIngestMessage(
        operation = EsIngestMessageOperation.Update,
        update = Some(
          personRequestToMessage(AsyncPersonUpdateRequest(id, doc, denorm))
        )
      ),
      messageGroupId = id.toString // Handle updates for this item in order
    )
  }

  def queuePersonDenormalization(id: UUID): Future[Unit] = {
    personDenormQueue
      .queue(
        EsDenormalizePersonMessage(personId = id),
        messageGroupId = id.toString
      )
      .map(_ => {})
  }

  private def personRequestToMessage(
    request: AsyncPersonUpdateRequest
  ): EsIngestUpdate = {
    EsIngestUpdate(
      index = teletrackerConfig.elasticsearch.people_index_name,
      id = request.id.toString,
      itemType = ItemType.Person,
      doc = request.doc,
      script = request.script,
      itemDenorm = None,
      personDenorm = request.denorm
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

  object AsyncPersonUpdateRequest {
    def apply(
      id: UUID,
      doc: Json,
      denorm: Option[EsIngestPersonDenormArgs]
    ): AsyncPersonUpdateRequest = AsyncPersonUpdateRequest(
      id = id,
      doc = Some(doc),
      script = None,
      denorm = denorm
    )
  }

  case class AsyncPersonUpdateRequest(
    id: UUID,
    doc: Option[Json],
    script: Option[Json],
    denorm: Option[EsIngestPersonDenormArgs])
}
