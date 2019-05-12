package com.teletracker.service.controllers

import com.teletracker.service.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.service.model.DataResponse
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

  get("/caches", admin = true) { _: Request =>
    import com.teletracker.service.util.json.circe._
    import io.circe.generic.auto._
    import io.circe.shapes._
    import io.circe.syntax._

    Future {
      val jsonString = DataResponse.complex(
        Map(
          "tmdbLocalCache" -> tmdbLocalCache.getAll().asJson,
          "justWatchLocalCache" -> justWatchLocalCache.getAll().asJson
        )
      )

      response.ok(jsonString).contentTypeJson()
    }
  }
}
