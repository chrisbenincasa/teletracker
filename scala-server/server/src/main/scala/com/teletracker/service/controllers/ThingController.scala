package com.teletracker.service.controllers

import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.auth.RequestContext._
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.CanParseFieldFilter
import com.teletracker.common.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class ThingController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v1/things") {
    filter[JwtAuthFilter].apply {
      get("/?") { req: BatchGetThingsRequest =>
        val selectFields = parseFieldsOrNone(req.fields)

        thingsDbAccess
          .findThingsByIds(req.thingIds.toSet, selectFields)
          .map(thingsById => {
            response.ok
              .contentTypeJson()
              .body(DataResponse.complex(thingsById.mapValues(_.toPartial)))
          })
      }
    }

    filter[JwtAuthFilter].apply {
      get("/:thingId/user-details") { req: GetThingRequest =>
        thingsDbAccess
          .getThingUserDetails(req.request.authContext.userId, req.thingId)
          .map(details => {
            response.ok.contentTypeJson().body(DataResponse(details))
          })
      }
    }
  }
}

case class GetThingRequest(
  @RouteParam thingId: UUID,
  request: Request)

case class BatchGetThingsRequest(
  @QueryParam(commaSeparatedList = true) thingIds: List[UUID],
  @QueryParam fields: Option[String],
  request: Request)
    extends InjectedRequest
