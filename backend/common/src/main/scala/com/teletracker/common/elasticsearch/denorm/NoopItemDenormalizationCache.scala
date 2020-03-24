package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import scala.concurrent.Future

class NoopItemDenormalizationCache extends ItemDenormalizationCache {
  override def get(
    key: (ExternalSource, String, ItemType)
  ): Future[Option[DenormalizationCacheItem]] = Future.successful(None)

  override def getBatch(keys: Set[(ExternalSource, String, ItemType)]): Future[
    Map[(ExternalSource, String, ItemType), DenormalizationCacheItem]
  ] = Future.successful(Map.empty)

  override def set(
    key: (ExternalSource, String, ItemType),
    value: DenormalizationCacheItem
  ): Future[Unit] = Future.unit

  override def setBatch(
    pairs: Map[(ExternalSource, String, ItemType), DenormalizationCacheItem]
  ): Future[Unit] = Future.unit
}
