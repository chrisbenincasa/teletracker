package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.auth.RequestContext._
import com.chrisbenincasa.services.teletracker.auth.JwtAuthFilter
import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TvShowController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1/shows") {
    get("/:id") { req: GetShowRequest =>
      thingsDbAccess.findShowById(req.id, withAvailability = true).map(result => {
        if (result.isEmpty) {
          response.status(404)
        } else {
          response.ok.contentTypeJson().body(DataResponse.complex(result.get))
        }
      })
    }

    get("/:id/availability") { req: GetShowRequest =>
      thingsDbAccess.findShowById(req.id, withAvailability = true).map(result => {
        if (result.isEmpty) {
          response.status(404)
        } else {
          response.ok.contentTypeJson().body(DataResponse.complex(result.get))
        }
      })
    }
  }
}

case class GetShowRequest(
  @RouteParam id: Int,
  request: Request
)
