package com.teletracker.service.controllers

import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.db.ThingsDbAccess
import com.teletracker.service.model.DataResponse
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TvShowController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1/shows") {
    filter[JwtAuthFilter].apply {
      get("/:id") { req: GetShowRequest =>
        val userId = req.request.authContext.user.id
        val thingDetailsFut = thingsDbAccess.getThingUserDetails(userId, req.id)

        val showByIdFut = thingsDbAccess.findShowByIdBasic(req.id)

        for {
          thingDetails <- thingDetailsFut
          showById <- showByIdFut
        } yield {
          if (showById.isEmpty) {
            response.status(404)
          } else {
            val finalResult = showById.get.withUserMetadata(thingDetails)
            response.ok
              .contentTypeJson()
              .body(DataResponse.complex(finalResult))
          }
        }
      }

      get("/:id/availability") { req: GetShowRequest =>
        thingsDbAccess
          .findShowById(req.id, withAvailability = true)
          .map(result => {
            if (result.isEmpty) {
              response.status(404)
            } else {
              response.ok
                .contentTypeJson()
                .body(DataResponse.complex(result.get))
            }
          })
      }
    }
  }
}

case class GetShowRequest(
  @RouteParam id: Int,
  request: Request)
