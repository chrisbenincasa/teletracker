package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.{
  SearchOptions,
  SearchRankingMode,
  ThingsDbAccess,
  UserThingDetails
}
import com.teletracker.common.db.model.{PartialThing, ThingType}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.DataResponse
import com.teletracker.common.model.tmdb._
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import com.teletracker.common.util.json.circe._
import com.teletracker.service.auth.RequestContext._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import io.circe.shapes._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SearchController @Inject()(
  config: TeletrackerConfig,
  thingsDbAccess: ThingsDbAccess,
  tmdbClient: TmdbClient,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    get("/search") { req: Request =>
      val query = req.params("query")

      tmdbClient
        .makeRequest[SearchResult]("search/multi", Seq("query" -> query))
        .flatMap(handleSearchMultiResult(req.authContext.map(_.userId), _))
        .map(result => {
          response.ok.contentTypeJson().body(DataResponse.complex(result))
        })
    }
  }

  prefix("/api/v2") {
    get("/search") { req: SearchRequest =>
      val query = req.query
      val mode = req.rankingMode.getOrElse(SearchRankingMode.Popularity)
      val options = SearchOptions(mode, req.types.map(_.toSet))

      for {
        things <- thingsDbAccess.searchForThings(query, options)
        thingIds = things.map(_.id)
        thingUserDetails <- getThingUserDetails(
          req.request.authContext
            .map(_.userId),
          thingIds.toSet
        )

      } yield {
        val allThings = things.map(thing => {
          val meta = thingUserDetails
            .getOrElse(thing.id, UserThingDetails.empty)
          thing.toPartial.withUserMetadata(meta)
        })

        response.ok.contentTypeJson().body(DataResponse.complex(allThings))
      }
    }
  }

  private def getThingUserDetails(
    userId: Option[String],
    thingIds: Set[UUID]
  ): Future[Map[UUID, UserThingDetails]] = {
    userId
      .map(
        thingsDbAccess
          .getThingsUserDetails(
            _,
            thingIds
          )
      )
      .getOrElse(Future.successful(Map.empty))
  }

  private def handleSearchMultiResult(
    userId: Option[String],
    result: SearchResult
  ): Future[List[PartialThing]] = {
    handleSearchMultiResult(userId, result.results)
  }

  private def handleSearchMultiResult(
    userId: Option[String],
    results: List[MultiTypeXor]
  ): Future[List[PartialThing]] = {
    for {
      popularItems <- tmdbSynchronousProcessor.processMixedTypes(results)
      thingIds = popularItems.map(_.id)
      thingUserDetails <- getThingUserDetails(userId, thingIds.toSet)
    } yield {
      popularItems.map(thing => {
        val meta = thingUserDetails
          .getOrElse(thing.id, UserThingDetails.empty)
        thing.withUserMetadata(meta)
      })
    }
  }
}

case class SearchRequest(
  @QueryParam query: String,
  @QueryParam rankingMode: Option[SearchRankingMode],
  @QueryParam(commaSeparatedList = true) types: Option[List[ThingType]],
  request: Request)
    extends InjectedRequest
