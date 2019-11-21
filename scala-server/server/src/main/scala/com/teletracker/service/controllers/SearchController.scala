package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.Bookmark
import com.teletracker.common.db.access.{
  SearchOptions,
  SearchRankingMode,
  ThingsDbAccess,
  UserThingDetails
}
import com.teletracker.common.db.model.{PartialThing, ThingType}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemSearch}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.model.tmdb._
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import com.teletracker.common.util.CanParseFieldFilter
import com.teletracker.common.util.json.circe._
import com.teletracker.service.auth.RequestContext._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{Max, Min}
import io.circe.shapes._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SearchController @Inject()(
  config: TeletrackerConfig,
  thingsDbAccess: ThingsDbAccess,
  tmdbClient: TmdbClient,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor,
  itemSearch: ItemSearch
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
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
      val fields = parseFieldsOrNone(req.fields)
      val mode = req.rankingMode.getOrElse(SearchRankingMode.Popularity)
      val bookmark = req.bookmark.map(Bookmark.parse)

      val options =
        SearchOptions(mode, req.types.map(_.toSet), req.limit, bookmark)

      for {
        (things, bookmark) <- thingsDbAccess.searchForThings(
          query,
          options,
          fields
        )
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

        response.ok
          .contentTypeJson()
          .body(
            DataResponse.forDataResponse(
              DataResponse(allThings, Some(Paging(bookmark.map(_.encode))))
            )
          )
      }
    }
  }

  import TeletrackerController._

  prefix("/api/v3") {
    get("/search") { req: SearchRequest =>
      val query = req.query
      val fields = parseFieldsOrNone(req.fields)
      val mode = req.rankingMode.getOrElse(SearchRankingMode.Popularity)
      val bookmark = req.bookmark.map(Bookmark.parse)

      val options =
        SearchOptions(mode, req.types.map(_.toSet), req.limit, bookmark)

      for {
        result <- itemSearch.fullTextSearch(
          query,
          options
        )
      } yield {
        val allThings =
          result.items.map(_.scopeToUser(req.request.authenticatedUserId))

        response.ok
          .contentTypeJson()
          .body(
            DataResponse.forDataResponse(
              DataResponse(
                allThings,
                Some(Paging(result.bookmark.map(_.encode)))
              )
            )
          )
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
      // TODO we can use the built-in thing user details here
      popularItems <- tmdbSynchronousProcessor.processMixedTypes(results, None)
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
  @QueryParam fields: Option[String],
  @QueryParam(commaSeparatedList = true) types: Option[List[ThingType]],
  @QueryParam bookmark: Option[String],
  @QueryParam @Max(50) @Min(0) limit: Int = 20,
  request: Request)
    extends InjectedRequest
