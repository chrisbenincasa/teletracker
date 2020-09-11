package com.teletracker.service.controllers

import com.teletracker.common.db.model.{ItemType, OfferType}
import com.teletracker.common.db.{Bookmark, SearchScore}
import com.teletracker.common.elasticsearch.BinaryOperator
import com.teletracker.common.elasticsearch.model.PeopleCreditsFilter
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.common.util.{CanParseFieldFilter, OpenDateRange}
import com.teletracker.service.api.model.Person
import com.teletracker.service.api.{
  AvailabilityFilters,
  ItemApi,
  ItemSearchRequest
}
import com.teletracker.service.auth.CurrentAuthenticatedUser
import com.teletracker.service.controllers.annotations.{
  ItemReleaseYear,
  RatingRange
}
import com.teletracker.service.controllers.params.RangeParser
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{Max, Min}
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext
import scala.util.Try

class SearchController @Inject()(
  itemApi: ItemApi,
  authedUser: Provider[Option[CurrentAuthenticatedUser]]
)(implicit executionContext: ExecutionContext)
    extends BaseController
    with CanParseFieldFilter {

  prefix("/api/v3") {
    get("/search") { req: SearchRequest =>
      val query = req.query

      for {
        result <- itemApi.fullTextSearch(
          authedUser.get().map(_.userId),
          query,
          makeSearchRequest(req)
        )
      } yield {
        val allThings =
          result.items.map(_.scopeToUser(req.request.authenticatedUserId))

        response.okCirceJsonResponse(
          DataResponse(
            allThings,
            Some(Paging(result.bookmark.map(_.encode)))
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
        response.okCirceJsonResponse(
          DataResponse(
            result.items.map(Person.fromEsPerson(_, None)),
            Some(Paging(result.bookmark.map(_.encode)))
          )
        )
      }
    }
  }

  import com.teletracker.common.util.Monoidal._
  import cats.instances.all._

  private def makeSearchRequest(req: SearchRequest) = {
    ItemSearchRequest(
      genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
      networks = Some(req.networks).filter(_.nonEmpty),
      allNetworks = Some(req.networks).map(_ == Set(ItemSearchRequest.All)),
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
            PeopleCreditsFilter(
              req.cast.toSeq,
              req.crew.toSeq,
              BinaryOperator.And
            )
          )
        else None,
      imdbRating = req.imdbRating.flatMap(RangeParser.parseRatingString),
      availabilityFilters = Some(
        AvailabilityFilters(
          offerTypes = req.offerTypes
            .flatMap(ot => Try(OfferType.fromString(ot)).toOption)
            .ifEmptyOption,
          presentationTypes = None
        )
      )
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
  @QueryParam @RatingRange(min = 0.0d, max = 10.0d) imdbRating: Option[String],
  @QueryParam(commaSeparatedList = true)
  offerTypes: Set[String] = Set(),
  request: Request)
    extends InjectedRequest
