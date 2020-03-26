package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.util.Slug
import java.util.UUID
import scala.concurrent.Future

trait ItemDenormalizationCache {
  type Key = (ExternalSource, String, ItemType)

  def get(key: Key): Future[Option[DenormalizationCacheItem]]

  def getBatch(keys: Set[Key]): Future[Map[Key, DenormalizationCacheItem]]

  def set(
    key: Key,
    value: DenormalizationCacheItem
  ): Future[Unit]

  def setBatch(pairs: Map[Key, DenormalizationCacheItem]): Future[Unit]
}

case class DenormalizationCacheItem(
  id: UUID,
  slug: Option[Slug])
