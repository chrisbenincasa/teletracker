package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  tmdbLocalCache: TmdbLocalCache,
  justWatchLocalCache: JustWatchLocalCache
)(implicit executionContext: ExecutionContext) extends Controller {
  post("/cache/clear", admin = true) { _: Request =>
    Future.sequence(
      List(
        tmdbLocalCache.clear(),
        justWatchLocalCache.clear()
      )
    )
  }
}
