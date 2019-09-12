package com.teletracker.service.controllers

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.CanParseFieldFilter
import com.teletracker.common.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import io.circe.generic.auto._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AvailabilityController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v1/availability") {
    get("/new") { req: UpcomingAvailabilityRequest =>
      thingsDbAccess
        .findPastAvailability(req.days.getOrElse(30), req.networkIds)
        .map(avs => {
          DataResponse.complex(avs)
        })
    }

    get("/upcoming") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      thingsDbAccess
        .findFutureAvailability(
          req.days.getOrElse(30),
          req.networkIds,
          selectFields
        )
        .map(avs => {
          response.ok.contentTypeJson().body(DataResponse.complex(avs))
        })
    }

    get("/all") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      thingsDbAccess
        .findRecentAvailability(
          req.days.getOrElse(30),
          req.networkIds,
          selectFields
        )
        .map(avs => {
          response.ok.contentTypeJson().body(DataResponse.complex(avs))
        })
    }
  }
}

case class UpcomingAvailabilityRequest(
  @QueryParam(commaSeparatedList = true) networkIds: Option[Set[Int]],
  @QueryParam days: Option[Int],
  @QueryParam fields: Option[String])
