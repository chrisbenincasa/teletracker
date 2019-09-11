package com.teletracker.service.controllers

import com.teletracker.common.cache.{JustWatchLocalCache, TmdbLocalCache}
import com.teletracker.common.db.access.{SyncThingsDbAccess, ThingsDbAccess}
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.model.DataResponse
import com.teletracker.common.model.tmdb.{MovieId, PersonId, TvShowId}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbProcessMessage
import com.teletracker.common.process.tmdb.TmdbProcessMessage.{
  ProcessMovie,
  ProcessPerson,
  ProcessTvShow
}
import com.teletracker.service.api.ThingApi
import com.teletracker.service.util.HasThingIdOrSlug
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import shapeless.tag
import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
  tmdbLocalCache: TmdbLocalCache,
  justWatchLocalCache: JustWatchLocalCache,
  thingsDbAccess: SyncThingsDbAccess,
  thingsApi: ThingApi,
  processQueue: ProcessQueue[TmdbProcessMessage]
)(implicit executionContext: ExecutionContext)
    extends Controller {
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
    import com.teletracker.common.util.json.circe._

    (HasThingIdOrSlug.parse(req.getParam("thingId")) match {
      case Left(id)    => thingsDbAccess.findThingByIdRaw(id)
      case Right(slug) => thingsDbAccess.findThingBySlugRaw(slug)
    }).map {
      case None => response.notFound
      case Some(thing) =>
        response.ok(DataResponse.complex(thing.toPartial)).contentTypeJson()
    }
  }

  get("/admin/finatra/partial-things/:thingId", admin = true) { req: Request =>
    import com.teletracker.common.util.json.circe._

    thingsApi
      .getThing(
        None,
        req.getParam("thingId"),
        ThingType.fromString(req.getParam("type"))
      )
      .map {
        case None => response.notFound
        case Some(thing) =>
          response.ok(DataResponse.complex(thing)).contentTypeJson()
      }
  }

  post("/refresh-thing", admin = true) { req: RefreshThingRequest =>
    (req.idOrSlug match {
      case Left(id)    => thingsDbAccess.findThingById(id, ThingType.Movie)
      case Right(slug) => thingsDbAccess.findThingBySlug(slug, ThingType.Movie)
    }).flatMap {
      case None =>
        Future.successful(response.notFound("Thing not found"))
      case Some(thing) =>
        thingsDbAccess.findExternalIds(thing.id).flatMap {
          case None =>
            Future.successful(response.notFound("External ids not found"))
          case Some(externalId) =>
            val message = TmdbProcessMessage
              .make(ProcessMovie(tag[MovieId](externalId.tmdbId.get.toInt)))
            processQueue.enqueue(message).map(_ => response.accepted)
        }
    }
  }

  post("/tmdb/scrape", admin = true) { req: ScrapeTmdbRequest =>
    val message = req.thingType match {
      case ThingType.Movie =>
        TmdbProcessMessage
          .make(ProcessMovie(tag[MovieId](req.id)))
      case ThingType.Show =>
        TmdbProcessMessage
          .make(ProcessTvShow(tag[TvShowId](req.id)))
      case ThingType.Person =>
        TmdbProcessMessage
          .make(ProcessPerson(tag[PersonId](req.id)))

    }

    processQueue.enqueue(message).map(_ => response.accepted)
  }
}

case class RefreshThingRequest(thingId: String) extends HasThingIdOrSlug
case class ScrapeTmdbRequest(
  id: Int,
  thingType: ThingType)
