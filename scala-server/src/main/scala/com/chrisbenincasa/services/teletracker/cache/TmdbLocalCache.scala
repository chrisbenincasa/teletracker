package com.chrisbenincasa.services.teletracker.cache

import com.chrisbenincasa.services.teletracker.db.model.ThingType
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntity.Entities
import com.google.common.cache.{Cache, CacheBuilder}
import javax.inject.{Singleton, Inject => JInject}
import shapeless.ops.coproduct.{Inject, Selector}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class TmdbLocalCache @JInject()(implicit executionContext: ExecutionContext) {
  private val cache: Cache[String, TmdbEntity.Entities] = CacheBuilder.newBuilder()
    .expireAfterWrite(30, TimeUnit.MINUTES)
    .maximumSize(1000)
    .build()

  def getOrSetEntity[T](thingType: ThingType,
    id: String, f: => Future[T]
  )(implicit inj: Inject[Entities, T], select: Selector[Entities, T]): Future[T] = {
    val key = keyForType(thingType, id)

    Option(cache.getIfPresent(key)) match {
      case Some(value) =>
        select(value)
          .map(Future.successful)
          .getOrElse(Future.failed(new IllegalStateException(s"Found unexpected type for key: $key")))

      case None =>
        f.andThen {
          case Success(value) => cache.put(key, inj(value))
        }
    }
  }

  private def keyForType(thingType: ThingType, id: String): String = {
    s"$thingType.$id"
  }
}
