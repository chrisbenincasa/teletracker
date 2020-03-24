package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.db.{Bookmark, SearchScore}
import com.teletracker.common.elasticsearch.{
  BinaryOperator,
  ItemSearch,
  PersonLookup
}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.common.util.{CanParseFieldFilter, OpenDateRange}
import com.teletracker.service.api
import com.teletracker.service.api.ItemApi
import com.teletracker.service.api.model.Person
import com.teletracker.service.controllers.annotations.ItemReleaseYear
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{Max, Min}
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class SearchController @Inject()(
  config: TeletrackerConfig,
  itemSearch: ItemSearch,
  itemApi: ItemApi,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  import TeletrackerController._

  prefix("/api/v3") {
    get("/search") { req: SearchRequest =>
      val query = req.query

      for {
        result <- itemApi.fullTextSearch(
          query,
          makeSearchRequest(req)
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

  prefix("/api/v2/people") {
    get("/search") { req: SearchRequest =>
      val query = req.query

      for {
        result <- itemApi.fullTextSearchPeople(
          query,
          makeSearchRequest(req)
        )
      } yield {
        response.ok
          .contentTypeJson()
          .body(
            DataResponse.forDataResponse(
              DataResponse(
                result.items.map(Person.fromEsPerson(_, None)),
                Some(Paging(result.bookmark.map(_.encode)))
              )
            )
          )
      }
    }
  }

  private def makeSearchRequest(req: SearchRequest) = {
    api.ItemSearchRequest(
      genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
      networks = Some(req.networks).filter(_.nonEmpty),
      itemTypes = Some(
        req.itemTypes.flatMap(t => Try(ItemType.fromString(t)).toOption)
      ),
      sortMode = SearchScore(),
      limit = req.limit,
      bookmark = req.bookmark.map(Bookmark.parse),
      releaseYear = Some(
        OpenDateRange(
          req.minReleaseYear.map(LocalDateUtils.localDateAtYear),
          req.maxReleaseYear.map(LocalDateUtils.localDateAtYear)
        )
      ),
      peopleCredits =
        if (req.cast.nonEmpty || req.crew.nonEmpty)
          Some(
            api.PeopleCreditsFilter(
              req.cast.toSeq,
              req.crew.toSeq,
              BinaryOperator.And
            )
          )
        else None
    )
  }
}

case class SearchRequest(
  @QueryParam
  query: String,
  @QueryParam(commaSeparatedList = true)
  itemTypes: Set[String] = Set(ItemType.Movie, ItemType.Show).map(_.toString),
  @QueryParam
  bookmark: Option[String],
  @Min(0) @Max(50) @QueryParam
  limit: Int = GetItemsRequest.DefaultLimit,
  @QueryParam(commaSeparatedList = true)
  networks: Set[String] = Set.empty,
  @QueryParam
  fields: Option[String] = None,
  @QueryParam(commaSeparatedList = true)
  genres: Set[Int] = Set.empty,
  @QueryParam @ItemReleaseYear
  minReleaseYear: Option[Int],
  @QueryParam @ItemReleaseYear
  maxReleaseYear: Option[Int],
  @QueryParam(commaSeparatedList = true)
  cast: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true)
  crew: Set[String] = Set.empty,
  request: Request)
    extends InjectedRequest
