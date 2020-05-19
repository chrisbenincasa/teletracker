package com.teletracker.common.elasticsearch.async

import com.teletracker.common.aws.sqs.SqsFifoQueue
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.pubsub.{
  EsIngestIndex,
  EsIngestItemDenormArgs,
  EsIngestItemExternalIdMapping,
  EsIngestMessage,
  EsIngestMessageOperation,
  EsIngestUpdate
}
import io.circe.Json
import javax.inject.Inject
import java.util.UUID
import io.circe.syntax._
import scala.concurrent.Future

class EsIngestQueue @Inject()(
  queue: SqsFifoQueue[EsIngestMessage],
  teletrackerConfig: TeletrackerConfig) {

  def queueItemInsert(esItem: EsItem): Future[Option[EsIngestMessage]] = {
    queue.queue(
      EsIngestMessage(
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
      )
    )
  }

  def queueItemUpdate(
    id: UUID,
    itemType: ItemType,
    doc: Json,
    denorm: Option[EsIngestItemDenormArgs]
  ): Future[Option[EsIngestMessage]] = {
    queue.queue(
      EsIngestMessage(
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
      )
    )
  }
}
