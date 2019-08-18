package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.{ThingsDbAccess, UserThingDetails}
import com.teletracker.common.db.model.PartialThing
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.DataResponse
import com.teletracker.common.model.tmdb._
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import com.teletracker.common.util.json.circe._
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.auth.RequestContext._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import io.circe.shapes._
import javax.inject.Inject
import shapeless.Coproduct
import scala.concurrent.{ExecutionContext, Future}

class SearchController @Inject()(
  config: TeletrackerConfig,
  thingsDbAccess: ThingsDbAccess,
  tmdbClient: TmdbClient,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    filter[JwtAuthFilter].apply {
      get("/search") { req: Request =>
        val query = req.params("query")

        tmdbClient
          .makeRequest[SearchResult]("search/multi", Seq("query" -> query))
          .flatMap(handleSearchMultiResult(req.authContext.userId, _))
          .map(result => {
            response.ok.contentTypeJson().body(DataResponse.complex(result))
          })
      }
    }
  }

  private def handleSearchMultiResult(
    userId: String,
    result: SearchResult
  ): Future[List[PartialThing]] = {
    handleSearchMultiResult(userId, result.results)
  }

  private def handleSearchMultiResult(
    userId: String,
    results: List[MultiTypeXor]
  ): Future[List[PartialThing]] = {

    for {
      popularItems <- tmdbSynchronousProcessor.processMixedTypes(results)
      thingIds = popularItems.map(_.id)
      thingUserDetails <- thingsDbAccess
        .getThingsUserDetails(userId, thingIds.toSet)
    } yield {
      popularItems.map(thing => {
        val meta = thingUserDetails
          .getOrElse(thing.id, UserThingDetails.empty)
        thing.withUserMetadata(meta)
      })
    }
  }
}
