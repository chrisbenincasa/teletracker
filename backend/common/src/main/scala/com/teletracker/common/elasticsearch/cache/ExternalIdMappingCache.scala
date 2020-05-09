package com.teletracker.common.elasticsearch.cache

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsExternalId
import javax.inject.{Inject, Singleton}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

@Singleton
class ExternalIdMappingCache @Inject()() {
  private val cache = new ConcurrentHashMap[(EsExternalId, ItemType), UUID]()

  def get(
    esExternalId: EsExternalId,
    itemType: ItemType
  ): Option[UUID] = {
    Option(cache.get(esExternalId -> itemType))
  }

  def getAll(
    set: Set[(EsExternalId, ItemType)]
  ): Map[(EsExternalId, ItemType), UUID] = {
    cache
      .entrySet()
      .asScala
      .filter(entry => set.contains(entry.getKey))
      .map(entry => entry.getKey -> entry.getValue)
      .toMap
  }

  def put(
    esExternalId: EsExternalId,
    itemType: ItemType,
    uuid: UUID
  ) = {
    cache.put(esExternalId -> itemType, uuid)
  }

  def putAll(m: Map[(EsExternalId, ItemType), UUID]) = {
    cache.putAll(m.asJava)
  }
}
