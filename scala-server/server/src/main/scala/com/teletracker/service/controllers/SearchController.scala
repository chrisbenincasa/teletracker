package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.SearchRankingMode
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.db.{Bookmark, SearchOptions}
import com.teletracker.common.elasticsearch.ItemSearch
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.CanParseFieldFilter
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{Max, Min}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SearchController @Inject()(
  config: TeletrackerConfig,
  itemSearch: ItemSearch
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
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
