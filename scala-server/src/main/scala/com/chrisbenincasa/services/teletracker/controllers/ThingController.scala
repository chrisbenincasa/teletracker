package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.auth.JwtAuthFilter
import com.chrisbenincasa.services.teletracker.auth.RequestContext._
import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ThingController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1/things") {
    filter[JwtAuthFilter].apply {
      get("/:thingId/user-details") { req: GetThingRequest =>
        thingsDbAccess.getThingUserDetails(req.request.authContext.user.id, req.thingId).map(details => {
          response.ok.contentTypeJson().body(DataResponse(details))
        })
      }
    }
  }
}

case class GetThingRequest(
  @RouteParam thingId: Int,
  request: Request
)
