package com.teletracker.service.controllers

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.db.{Bookmark, Popularity, Recent, SortMode}
import com.teletracker.common.elasticsearch.{BinaryOperator, ItemSearch}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.common.util.{
  CanParseFieldFilter,
  ClosedNumericRange,
  Field,
  NetworkCache,
  OpenDateRange,
  OpenNumericRange
}
import com.teletracker.service.api
import com.teletracker.service.api.model.Item
import com.teletracker.service.api.{ItemApi, ItemSearchRequest}
import com.teletracker.service.controllers.TeletrackerController._
import com.teletracker.service.controllers.annotations.{
  ItemReleaseYear,
  RatingRange
}
import com.teletracker.service.controllers.params.RangeParser
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import com.twitter.finatra.validation.{
  Max,
  MethodValidation,
  Min,
  ValidationResult
}
import javax.inject.Inject
import java.time.LocalDate
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class PopularItemsController @Inject()(
  tmdbClient: TmdbClient,
  networkCache: NetworkCache,
  popularItemSearch: ItemSearch,
  itemApi: ItemApi
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  private val defaultFields = List(Field("id"))

  prefix("/api/v2") {
    get("/explore") { req: GetItemsRequest =>
      val searchRequest = makeSearchRequest(req)
      executeRequest(req.authenticatedUserId, searchRequest)
    }

    get("/popular") { req: GetItemsRequest =>
      val searchRequest = makeSearchRequest(req)
      executeRequest(req.authenticatedUserId, searchRequest)
    }
  }

  private def executeRequest(
    userId: Option[String],
    searchRequest: ItemSearchRequest
  ) = {
    itemApi
      .search(
        searchRequest
      )
      .map(popularItems => {
        val items =
          popularItems.items
            .map(_.scopeToUser(userId))
            .map(Item.fromEsItem(_, Nil))

        DataResponse.forDataResponse(
          DataResponse(items).withPaging(
            Paging(popularItems.bookmark.map(_.encode))
          )
        )
      })
      .map(response.ok(_).contentTypeJson())
  }

  private def makeSearchRequest(req: GetItemsRequest) = {
    api.ItemSearchRequest(
      genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
      networks = Some(req.networks).filter(_.nonEmpty),
      itemTypes = Some(
        req.itemTypes.flatMap(t => Try(ItemType.fromString(t)).toOption)
      ),
      sortMode = req.sort.map(SortMode.fromString).getOrElse(Recent()),
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
        else None,
      imdbRating = req.imdbRating.flatMap(RangeParser.parseRatingString)
    )
  }
}

object GetItemsRequest {
  final val DefaultLimit = 10
  final val MaxYear = LocalDate.now().getYear + 5
}

case class GetItemsRequest(
  @QueryParam(commaSeparatedList = true) itemTypes: Set[String] =
    Set(ItemType.Movie, ItemType.Show).map(_.toString),
  @QueryParam bookmark: Option[String],
  @QueryParam sort: Option[String],
  @Min(0) @Max(50) @QueryParam limit: Int = GetItemsRequest.DefaultLimit,
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam fields: Option[String] = None,
  @QueryParam(commaSeparatedList = true) genres: Set[Int] = Set.empty,
  @QueryParam @ItemReleaseYear minReleaseYear: Option[Int],
  @QueryParam @ItemReleaseYear maxReleaseYear: Option[Int],
  @QueryParam(commaSeparatedList = true) cast: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) crew: Set[String] = Set.empty,
  @QueryParam @RatingRange(min = 0.0d, max = 10.0d) imdbRating: Option[String],
  request: Request)
    extends InjectedRequest {

  @MethodValidation
  def validateBookmark: ValidationResult = {
    bookmark
      .map(b => {
        ValidationResult.validate(
          Try(Bookmark.parse(b)).isSuccess,
          s"Invalid bookmark with format: $b"
        )
      })
      .getOrElse(ValidationResult.Valid())
  }
}
