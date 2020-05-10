package com.teletracker.common.elasticsearch.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.elasticsearch.model.EsExternalId
import javax.inject.{Inject, Singleton}
import java.util.UUID
import scala.collection.JavaConverters._

@Singleton
class ExternalIdMappingCache @Inject()() {
  private val cache = Caffeine
    .newBuilder()
    .maximumSize(25000)
    .build[(EsExternalId, ItemType), UUID]()

  def get(
    esExternalId: EsExternalId,
    itemType: ItemType
  ): Option[UUID] = {
    Option(cache.getIfPresent(esExternalId -> itemType))
  }

  def getAll(
    set: Set[(EsExternalId, ItemType)]
  ): Map[(EsExternalId, ItemType), UUID] = {
    cache
      .getAllPresent(set.asJava)
      .asScala
      .toMap
  }

  def put(
    esExternalId: EsExternalId,
    itemType: ItemType,
    uuid: UUID
  ): Unit = {
    cache.put(esExternalId -> itemType, uuid)
  }

  def putAll(m: Map[(EsExternalId, ItemType), UUID]): Unit = {
    cache.putAll(m.asJava)
  }
}
