package com.teletracker.service.controllers

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{EsItem, EsPerson}
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.CanParseFieldFilter
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api.ThingApi
import com.teletracker.service.api.model.Person
import com.teletracker.service.controllers.TeletrackerController._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ThingController @Inject()(
  thingsDbAccess: ThingsDbAccess,
  thingApi: ThingApi
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
    get("/:personId") { req: GetPersonRequest =>
      thingApi.getPersonViaSearch(req.authenticatedUserId, req.personId).map {
        case None => response.notFound
        case Some((person, credits)) =>
          response.ok(
            DataResponse.complex(
              Person.fromEsPerson(
                person,
                credits.items.map(item => item.id -> item).toMap
              )
            )
          )
      }
    }
  }
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
