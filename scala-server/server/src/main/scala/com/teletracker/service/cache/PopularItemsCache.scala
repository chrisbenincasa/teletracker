package com.teletracker.service.cache

import com.google.common.cache.CacheBuilder
import com.teletracker.common.db.model.PartialThing
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class PopularItemsCache @Inject()(
  tmdbClient: TmdbClient,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor
)(implicit executionContext: ExecutionContext) {
  private val cache =
    CacheBuilder
      .newBuilder()
      .expireAfterWrite(1, TimeUnit.DAYS)
      .build[java.lang.Integer, List[PartialThing]]()

  @volatile private var _loading: Future[List[PartialThing]] = _

  def getOrSet(): Future[List[PartialThing]] = {
    Option(cache.getIfPresent(0)) match {
      case Some(value) =>
        Future.successful(value)

      case None =>
        synchronized {
          if (_loading == null) {
            _loading = tmdbClient
              .getPopularMovies()
              .flatMap(results => {
                tmdbSynchronousProcessor.processMovies(results.results, None)
              })

            _loading.foreach(cache.put(0, _))
          }

          _loading
        }
    }
  }

  def clear(): Unit = {
    cache.invalidateAll()
  }
}
