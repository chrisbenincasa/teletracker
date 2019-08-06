package com.teletracker.common.cache

import com.google.common.cache.{Cache, CacheBuilder}
import com.teletracker.common.model.justwatch.{
  PopularItemsResponse,
  PopularSearchRequest
}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class JustWatchLocalCache @Inject()(
  implicit executionContext: ExecutionContext) {
  private val cache: Cache[PopularSearchRequest, PopularItemsResponse] =
    CacheBuilder
      .newBuilder()
      .expireAfterWrite(6, TimeUnit.HOURS)
      .maximumSize(1000)
      .build()

  def getOrSet(
    request: PopularSearchRequest,
    f: => Future[PopularItemsResponse]
  ): Future[PopularItemsResponse] = {
    Option(cache.getIfPresent(request)) match {
      case Some(value) => Future.successful(value)
      case None =>
        f.andThen {
          case Success(value) => cache.put(request, value)
        }
    }
  }

  def getAll(): Map[String, PopularItemsResponse] = {
    cache.asMap().asScala.toMap.map { case (k, v) => k.toString -> v }
  }

  def clear(): Future[Unit] = {
    Future {
      cache.invalidateAll()
    }
  }
}
