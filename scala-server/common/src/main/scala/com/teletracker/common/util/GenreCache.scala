package com.teletracker.common.util

import com.teletracker.common.db.access.{GenresDbAccess, NetworksDbAccess}
import com.teletracker.common.db.model._
import com.teletracker.common.util.GenreCache.GenreMap
import com.teletracker.common.util.Implicits._
import com.twitter.cache.ConcurrentMapCache
import com.twitter.util.{Future => TFuture}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

object GenreCache {
  type GenreMap = Map[(ExternalSource, String), Genre]
}

@Singleton
class GenreCache @Inject()(
  genresDbAccess: GenresDbAccess
)(implicit executionContext: ExecutionContext) {
  import GenreCache._

  private val cache = new ConcurrentMapCache[String, GenreMap](
    new ConcurrentHashMap[String, TFuture[GenreMap]]()
  )

  def get(): Future[GenreMap] = {
    cache.getOrElseUpdate("GENRES") {
      val p = com.twitter.util.Promise[GenreMap]()
      val sFut = genresDbAccess
        .findAllGenres()
        .map(_.map {
          case (ref, net) => (ref.externalSource -> ref.externalId) -> net
        }.toMap)

      sFut.onComplete(p.update(_))

      p
    }
  }
}
