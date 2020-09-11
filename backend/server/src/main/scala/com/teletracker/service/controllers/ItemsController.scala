package com.teletracker.service.controllers

import cats.instances.all._
import com.teletracker.common.db.{Bookmark, Recent, SearchRankingMode, SortMode}
import com.teletracker.common.db.model.{
  ItemType,
  OfferType,
  PersonAssociationType
}
import com.teletracker.common.elasticsearch.model.{
  EsItem,
  EsPerson,
  PeopleCreditsFilter
}
import com.teletracker.common.elasticsearch.{BinaryOperator, PersonLookup}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.Monoidal.toMonoidalExtras
import com.teletracker.common.util.{CanParseFieldFilter, OpenDateRange}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.time.LocalDateUtils
import com.teletracker.service.api
import com.teletracker.service.api.{
  AvailabilityFilters,
  ItemApi,
  ItemSearchRequest
}
import com.teletracker.service.api.model.{Item, Person}
import com.teletracker.service.auth.CurrentAuthenticatedUser
import com.teletracker.service.controllers.annotations.{
  ItemReleaseYear,
  RatingRange
}
import com.teletracker.service.controllers.params.RangeParser
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{
  Max,
  MethodValidation,
  Min,
  ValidationResult
}
import io.circe.generic.JsonCodec
import javax.inject.{Inject, Provider}
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

class ItemsController @Inject()(
  itemApi: ItemApi,
  personLookup: PersonLookup,
  authedUser: Provider[Option[CurrentAuthenticatedUser]]
)(implicit executionContext: ExecutionContext)
    extends BaseController
    with CanParseFieldFilter {
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

  prefix("/api/v2/items") {
    get("/:itemId/?") { req: GetItemRequest =>
      itemApi
        .getThingViaSearch(
          req.authenticatedUserId,
          req.itemId,
          Some(req.thingType),
          materializeRecommendations = req.includeRecommendations
        )
        .map {
          case None =>
            response.notFound

          case Some(found) =>
            response.okCirceJsonResponse(DataResponse(found))
        }
        .recover {
          case NonFatal(e) =>
            response.internalServerError
        }
    }

    get("/:itemId/recommendations") { req: GetItemRecommendationsRequest =>
      itemApi
        .getThingViaSearch(
          req.authenticatedUserId,
          req.itemId,
          Some(req.thingType),
          materializeRecommendations = true
        )
        .map {
          case None =>
            response.notFound

          case Some(found) =>
            response.okCirceJsonResponse(
              DataResponse(found.recommendations.getOrElse(Nil))
            )
        }
        .recover {
          case NonFatal(e) =>
            response.internalServerError
        }
    }
  }

  prefix("/api/v2/people") {
    get("/batch") { req: GetPersonBatchRequest =>
      itemApi
        .getPeopleViaSearch(req.personIds)
        .map(results => {
          response
            .okCirceJsonResponse(
              DataResponse(
                results.map(
                  Person.fromEsPerson(
                    _,
                    None
                  )
                )
              )
            )
        })
    }

    get("/:personId") { req: GetPersonRequest =>
      itemApi
        .getPersonViaSearch(
          requestingUserId = authedUser.get().map(_.userId),
          idOrSlug = req.personId,
          materializeCredits = req.materializeCredits,
          creditsLimit = req.creditsLimit.getOrElse(20)
        )
        .map {
          case None => response.notFound
          case Some(person) =>
            response
              .okCirceJsonResponse(
                DataResponse(person)
              )
        }
    }

    get("/:personId/credits") { req: GetPersonCreditsRequest =>
      val request = api.PersonCreditsRequest(
        genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
        networks = Some(req.networks).filter(_.nonEmpty),
        itemTypes = Some(
          req.itemTypes.flatMap(t => Try(ItemType.fromString(t)).toOption)
        ),
        creditTypes = Some(
          req.creditTypes.map(PersonAssociationType.fromString)
        ).filter(_.nonEmpty),
        sortMode = req.sort.map(SortMode.fromString).getOrElse(Recent()),
        limit = req.limit,
        bookmark = req.bookmark.map(Bookmark.parse),
        releaseYear = Some(
          OpenDateRange(
            req.minReleaseYear.map(LocalDateUtils.localDateAtYear),
            req.maxReleaseYear.map(LocalDateUtils.localDateAtYear)
          )
        ),
        availabilityFilters = Some(
          AvailabilityFilters(
            offerTypes = req.offerTypes
              .flatMap(ot => Try(OfferType.fromString(ot)).toOption)
              .ifEmptyOption,
            presentationTypes = None
          )
        )
      )

      itemApi
        .getPersonCredits(authedUser.get().map(_.userId), req.personId, request)
        .map(result => {
          val items =
            result.items.map(_.scopeToUser(req.authenticatedUserId))

          DataResponse(items).withPaging(
            Paging(result.bookmark.map(_.encode))
          )
        })
        .map(jsonResponse => {
          response.okCirceJsonResponse(jsonResponse)
        })
    }
  }

  private def executeRequest(
    userId: Option[String],
    searchRequest: ItemSearchRequest
  ) = {
    itemApi
      .search(
        authedUser.get().map(_.userId),
        searchRequest
      )
      .map(popularItems => {
        val items =
          popularItems.items
            .map(_.scopeToUser(userId))
            .map(Item.fromEsItem(_, Nil))

        DataResponse(items).withPaging(
          Paging(popularItems.bookmark.map(_.encode))
        )
      })
      .map(response.okCirceJsonResponse(_))
  }

  private def makeSearchRequest(req: GetItemsRequest) = {
    ItemSearchRequest(
      genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
      networks = Some(req.networks)
        .filter(_.nonEmpty)
        .filterNot(_.contains(ItemSearchRequest.All)),
      allNetworks = Some(req.networks)
        .filter(_.nonEmpty)
        .map(_ == Set(ItemSearchRequest.All)),
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

@JsonCodec
case class PersonResponse(
  person: EsPerson,
  castCreditsFull: Map[UUID, EsItem])

case class GetItemRequest(
  @RouteParam itemId: String,
  @QueryParam thingType: ItemType,
  @QueryParam includeRecommendations: Boolean = false,
  request: Request)
    extends InjectedRequest

case class GetItemRecommendationsRequest(
  @RouteParam itemId: String,
  @QueryParam thingType: ItemType,
  request: Request)
    extends InjectedRequest

case class BatchGetThingsRequest(
  thingIds: List[UUID],
  fields: Option[String],
  request: Request)
    extends InjectedRequest

case class GetPersonRequest(
  @RouteParam personId: String,
  @QueryParam fields: Option[String],
  @Min(0) @Max(20)
  @QueryParam creditsLimit: Option[Int],
  @QueryParam materializeCredits: Boolean = true,
  request: Request)
    extends InjectedRequest

case class GetPersonBatchRequest(
  @QueryParam(commaSeparatedList = true) personIds: List[String],
  request: Request)
    extends InjectedRequest

case class GetPersonCreditsRequest(
  @RouteParam personId: String,
  @QueryParam(commaSeparatedList = true) itemTypes: Set[String] =
    Set(ItemType.Movie, ItemType.Show).map(_.toString),
  @QueryParam(commaSeparatedList = true) creditTypes: Set[String] =
    PersonAssociationType.values().map(_.toString).toSet,
  @QueryParam bookmark: Option[String],
  @QueryParam(commaSeparatedList = true) networks: Set[String] = Set.empty,
  @QueryParam(commaSeparatedList = true) genres: Set[String] = Set.empty,
  @QueryParam sort: Option[String],
  @QueryParam desc: Option[Boolean],
  @Min(0) @Max(50) @QueryParam limit: Int = GetItemsRequest.DefaultLimit,
  @QueryParam @ItemReleaseYear minReleaseYear: Option[Int],
  @QueryParam @ItemReleaseYear maxReleaseYear: Option[Int],
  @QueryParam(commaSeparatedList = true)
  offerTypes: Set[String] = Set(),
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
  @QueryParam(commaSeparatedList = true)
  offerTypes: Set[String] = Set(),
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
