package com.teletracker.service.controllers

import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.auth.RequestContext._
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.CanParseFieldFilter
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api.ThingApi
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import java.util.UUID
import TeletrackerController._
import com.teletracker.common.db.model.ThingType
import scala.concurrent.ExecutionContext

class ThingController @Inject()(
  thingsDbAccess: ThingsDbAccess,
  thingApi: ThingApi
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v1/things") {
    filter[JwtAuthFilter] {

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
              response.notFound

            case Some(found) =>
              response.ok
                .contentTypeJson()
                .body(DataResponse.complex(found))
          }
      }
    }
  }
}

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
