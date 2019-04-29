package com.chrisbenincasa.services.teletracker.cache

import com.chrisbenincasa.services.teletracker.model.justwatch.{PopularItemsResponse, PopularSearchRequest}
import com.google.common.cache.{Cache, CacheBuilder}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class JustWatchLocalCache @Inject()(implicit executionContext: ExecutionContext) {
  private val cache: Cache[PopularSearchRequest, PopularItemsResponse] = CacheBuilder.newBuilder()
    .expireAfterWrite(6, TimeUnit.HOURS)
    .maximumSize(1000)
    .build()

  def getOrSet(request: PopularSearchRequest, f: => Future[PopularItemsResponse]): Future[PopularItemsResponse] = {
    Option(cache.getIfPresent(request)) match {
      case Some(value) => Future.successful(value)
      case None => f.andThen {
        case Success(value) => cache.put(request, value)
      }
    }
  }

  def clear(): Future[Unit] = {
    Future {
      cache.invalidateAll()
    }
  }
}
