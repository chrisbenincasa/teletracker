package com.teletracker.service.controllers

import com.teletracker.common.db.{Bookmark, Popularity, Recent, SortMode}
import com.teletracker.common.db.access.{
  SearchOptions,
  SearchRankingMode,
  ThingsDbAccess
}
import com.teletracker.common.db.model.{PersonAssociationType, ThingType}
import com.teletracker.common.elasticsearch.{EsItem, EsPerson, PersonLookup}
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.{CanParseFieldFilter, OpenDateRange}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api
import com.teletracker.service.api.{PersonCreditsRequest, ThingApi}
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
  thingsDbAccess: ThingsDbAccess,
  thingApi: ThingApi,
  personLookup: PersonLookup
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v1/things") {
    post("/batch/?") { req: BatchGetThingsRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      thingsDbAccess
        .findThingsByIds(req.thingIds.toSet, selectFields)
        .map(thingsById => {
          response.ok
            .contentTypeJson()
            .body(DataResponse.complex(thingsById.mapValues(_.toPartial)))
        })
    }

    get("/:thingId/?") { req: GetThingRequest =>
      thingApi
        .getThing(req.authenticatedUserId, req.thingId, req.thingType)
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

  prefix("/api/v2/items") {
    get("/:thingId/?") { req: GetThingRequest =>
      thingApi
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

  prefix("/api/v1/people") {
    get("/:personId") { req: GetPersonRequest =>
      thingApi.getPerson(req.authenticatedUserId, req.personId).map {
        case None => response.notFound
        case Some(person) =>
          response.ok(
            DataResponse.complex(person)
          )
      }
    }
  }

  prefix("/api/v2/people") {
    get("/search") { req: SearchRequest =>
      val query = req.query
      val fields = parseFieldsOrNone(req.fields)
      val mode = req.rankingMode.getOrElse(SearchRankingMode.Popularity)
      val bookmark = req.bookmark.map(Bookmark.parse)

      val options =
        SearchOptions(mode, None, req.limit, bookmark)

      for {
        result <- personLookup.fullTextSearch(
          query,
          options
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

    get("/batch") { req: GetPersonBatchRequest =>
      thingApi
        .getPeopleViaSearch(req.authenticatedUserId, req.personIds)
        .map(results => {
          response.ok(
            DataResponse.complex(
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
      thingApi.getPersonViaSearch(req.authenticatedUserId, req.personId).map {
        case None => response.notFound
        case Some((person, credits)) =>
          response.ok(
            DataResponse.complex(
              Person.fromEsPerson(
                person,
                Some(credits)
              )
            )
          )
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

      thingApi
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
