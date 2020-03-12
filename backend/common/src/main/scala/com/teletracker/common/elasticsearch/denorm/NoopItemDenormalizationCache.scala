package com.teletracker.common.elasticsearch.denorm

import com.teletracker.common.db.model.{ExternalSource, ThingType}
import scala.concurrent.Future

class NoopItemDenormalizationCache extends ItemDenormalizationCache {
  override def get(
    key: (ExternalSource, String, ThingType)
  ): Future[Option[DenormalizationCacheItem]] = Future.successful(None)

  override def getBatch(keys: Set[(ExternalSource, String, ThingType)]): Future[
    Map[(ExternalSource, String, ThingType), DenormalizationCacheItem]
  ] = Future.successful(Map.empty)

  override def set(
    key: (ExternalSource, String, ThingType),
    value: DenormalizationCacheItem
  ): Future[Unit] = Future.unit

  override def setBatch(
    pairs: Map[(ExternalSource, String, ThingType), DenormalizationCacheItem]
  ): Future[Unit] = Future.unit
}
