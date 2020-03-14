package com.teletracker.service.controllers

import com.teletracker.common.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.model.DataResponse
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbProcessMessage
import com.teletracker.common.util.HasThingIdOrSlug
import com.teletracker.service.api.ItemApi
import com.teletracker.service.auth.AdminFilter
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  tmdbLocalCache: TmdbLocalCache,
  justWatchLocalCache: JustWatchLocalCache,
  thingsApi: ItemApi,
  processQueue: ProcessQueue[TmdbProcessMessage],
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends Controller {

  // TODO put on admin server and open up admin server port on GCP
  filter[AdminFilter].get("/version") { _: Request =>
    response.ok.body(
      getClass.getClassLoader.getResourceAsStream("version_info.txt")
    )
  }

  post("/cache/clear", admin = true) { _: Request =>
    Future.sequence(
      List(
        tmdbLocalCache.clear(),
        justWatchLocalCache.clear()
      )
    )
  }

  get("/caches", admin = true) { _: Request =>
    import com.teletracker.common.util.json.circe._
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

  get("/admin/finatra/things/:thingId", admin = true) { req: Request =>
    (HasThingIdOrSlug.parse(req.getParam("thingId")) match {
      case Left(id) =>
        itemLookup.lookupItemsByIds(Set(id)).map(_.get(id).flatten)
      case Right(slug) =>
        itemLookup.lookupItemBySlug(
          slug,
          ThingType.fromString(req.getParam("type")),
          None
        )
    }).map {
      case None => response.notFound
      case Some(thing) =>
        response.ok(DataResponse.complex(thing)).contentTypeJson()
    }
  }
}

case class RefreshThingRequest(thingId: String) extends HasThingIdOrSlug
case class ScrapeTmdbRequest(
  id: Int,
  thingType: ThingType)
