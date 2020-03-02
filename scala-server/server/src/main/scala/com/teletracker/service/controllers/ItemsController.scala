package com.teletracker.service.controllers

import com.teletracker.common.db.{
  Bookmark,
  Recent,
  SearchOptions,
  SearchRankingMode,
  SortMode
}
import com.teletracker.common.db.model.{PersonAssociationType, ThingType}
import com.teletracker.common.elasticsearch.{EsItem, EsPerson, PersonLookup}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.{CanParseFieldFilter, OpenDateRange}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api
import com.teletracker.service.api.ItemApi
import com.teletracker.service.api.model.Person
import com.teletracker.service.controllers.TeletrackerController._
import com.teletracker.service.controllers.annotations.ItemReleaseYear
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{
  Max,
  MethodValidation,
  Min,
  ValidationResult
}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ItemsController @Inject()(
  itemApi: ItemApi,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v2/items") {
    get("/:thingId/?") { req: GetThingRequest =>
      itemApi
        .getThingViaSearch(
          req.authenticatedUserId,
          req.thingId,
          Some(req.thingType)
        )
        .map {
          case None =>
            Future.successful(response.notFound)

          case Some(found) =>
            response.ok
              .contentTypeJson()
              .body(DataResponse.complex(found))
        }
    }
  }

  prefix("/api/v2/people") {
    get("/batch") { req: GetPersonBatchRequest =>
      itemApi
        .getPeopleViaSearch(req.authenticatedUserId, req.personIds)
        .map(results => {
          response
            .ok(
              DataResponse.complex(
                results.map(
                  Person.fromEsPerson(
                    _,
                    None
                  )
                )
              )
            )
            .contentTypeJson()
        })
    }

    get("/:personId") { req: GetPersonRequest =>
      itemApi.getPersonViaSearch(req.authenticatedUserId, req.personId).map {
        case None => response.notFound
        case Some((person, credits)) =>
          response
            .ok(
              DataResponse.complex(
                Person.fromEsPerson(
                  person,
                  Some(credits)
                )
              )
            )
            .contentTypeJson()
      }
    }

    get("/:personId/credits") { req: GetPersonCreditsRequest =>
      val request = api.PersonCreditsRequest(
        genres = Some(req.genres.map(_.toString)).filter(_.nonEmpty),
        networks = Some(req.networks).filter(_.nonEmpty),
        itemTypes = Some(
          req.itemTypes.flatMap(t => Try(ThingType.fromString(t)).toOption)
        ),
        creditTypes = Some(
          req.creditTypes.map(PersonAssociationType.fromString)
        ).filter(_.nonEmpty),
        sortMode = req.sort.map(SortMode.fromString).getOrElse(Recent()),
        limit = req.limit,
        bookmark = req.bookmark.map(Bookmark.parse),
        releaseYear = Some(
          OpenDateRange(
            req.minReleaseYear.map(localDateAtYear),
            req.maxReleaseYear.map(localDateAtYear)
          )
        )
      )

      itemApi
        .getPersonCredits(req.authenticatedUserId, req.personId, request)
        .map(result => {
          val items =
            result.items.map(_.scopeToUser(req.authenticatedUserId))

          DataResponse.forDataResponse(
            DataResponse(items).withPaging(
              Paging(result.bookmark.map(_.encode))
            )
          )
        })
        .map(jsonResponse => {
          response.ok(jsonResponse).contentTypeJson()
        })
    }
  }

  // TODO: Common place
  private def localDateAtYear(year: Int): LocalDate = LocalDate.of(year, 1, 1)
}

@JsonCodec
case class PersonResponse(
  person: EsPerson,
  castCreditsFull: Map[UUID, EsItem])

case class GetThingRequest(
  @RouteParam thingId: String,
  @QueryParam thingType: ThingType,
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
  request: Request)
    extends InjectedRequest

case class GetPersonBatchRequest(
  @QueryParam(commaSeparatedList = true) personIds: List[String],
  request: Request)
    extends InjectedRequest

case class GetPersonCreditsRequest(
  @RouteParam personId: String,
  @QueryParam(commaSeparatedList = true) itemTypes: Set[String] =
    Set(ThingType.Movie, ThingType.Show).map(_.toString),
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
