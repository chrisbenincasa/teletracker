package com.teletracker.common.elasticsearch.denorm

import javax.inject.{Inject, Singleton}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InMemoryItemDenormalizationCache @Inject()(
  implicit executionContext: ExecutionContext)
    extends ItemDenormalizationCache {
  private val cache = new ConcurrentHashMap[Key, DenormalizationCacheItem]()

  override def get(key: Key): Future[Option[DenormalizationCacheItem]] =
    Future.successful(Option(cache.get(key)))

  override def getBatch(
    keys: Set[Key]
  ): Future[Map[Key, DenormalizationCacheItem]] = {
    Future.successful {
      keys.flatMap(key => Option(cache.get(key)).map(key -> _)).toMap
    }
  }

  override def set(
    key: Key,
    value: DenormalizationCacheItem
  ): Future[Unit] = {
    Future.successful(cache.put(key, value))
  }

  override def setBatch(
    pairs: Map[Key, DenormalizationCacheItem]
  ): Future[Unit] = {
    Future.sequence(pairs.map(Function.tupled(set))).map(_ => {})
  }
}
