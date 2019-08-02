package com.teletracker.service.controllers

import com.teletracker.service.db.access.ThingsDbAccess
import com.teletracker.service.model.DataResponse
import com.teletracker.service.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import io.circe.generic.auto._
import scala.concurrent.ExecutionContext

class AvailabilityController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1/availability") {
    get("/new") { req: UpcomingAvailabilityRequest =>
      thingsDbAccess
        .findPastAvailability(req.days.getOrElse(30), req.networkId)
        .map(avs => {
          DataResponse.complex(avs)
        })
    }

    get("/upcoming") { req: UpcomingAvailabilityRequest =>
      thingsDbAccess
        .findFutureAvailability(req.days.getOrElse(30), req.networkId)
        .map(avs => {
          DataResponse.complex(avs)
        })
    }
  }
}

case class UpcomingAvailabilityRequest(
  @QueryParam networkId: Option[Int],
  @QueryParam days: Option[Int])
