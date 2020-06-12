package com.teletracker.common.elasticsearch.lookups

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsExternalId
import java.util.UUID
import scala.concurrent.Future

trait ElasticsearchExternalIdMappingStore {
  def getItemIdForExternalId(
    externalId: EsExternalId,
    itemType: ItemType
  ): Future[Option[UUID]]

  def getItemIdsForExternalIds(
    lookupPairs: Set[(EsExternalId, ItemType)]
  ): Future[Map[(EsExternalId, ItemType), UUID]]

  def getExternalIdsForItemId(
    itemId: UUID,
    itemType: ItemType
  ): Future[List[EsExternalId]]

  def mapExternalId(
    externalId: EsExternalId,
    itemType: ItemType,
    id: UUID
  ): Future[Unit]

  def mapExternalIds(
    mappings: Map[(EsExternalId, ItemType), UUID]
  ): Future[Unit]

  def unmapExternalIds(ids: Set[(EsExternalId, ItemType)]): Future[Unit]
}
