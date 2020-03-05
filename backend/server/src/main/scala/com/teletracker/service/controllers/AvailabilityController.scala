package com.teletracker.service.controllers

import com.teletracker.common.elasticsearch.ItemAvailabilitySearch
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.{CanParseFieldFilter, NetworkCache}
import com.teletracker.service.api.model.Item
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AvailabilityController @Inject()(
  networkCache: NetworkCache,
  itemAvailabilitySearch: ItemAvailabilitySearch
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {

  prefix("/api/v2/availability") {
    get("/new") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      itemAvailabilitySearch
        .findNew(
          req.days.getOrElse(7),
          Some(req.networkIds.toSet)
        )
        .map(avs => {
          response.ok
            .contentTypeJson()
            .body(DataResponse.complex(avs.items.map(Item.fromEsItem(_))))
        })
    }

    get("/upcoming") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      itemAvailabilitySearch
        .findUpcoming(
          req.days.getOrElse(30),
          Some(req.networkIds.toSet)
        )
        .map(avs => {
          response.ok
            .contentTypeJson()
            .body(DataResponse.complex(avs.items.map(Item.fromEsItem(_))))
        })
    }

    get("/expiring") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      itemAvailabilitySearch
        .findExpiring(
          req.days.getOrElse(30),
          Some(req.networkIds.toSet)
        )
        .map(avs => {
          response.ok
            .contentTypeJson()
            .body(DataResponse.complex(avs.items.map(Item.fromEsItem(_))))
        })
    }
  }
}

case class UpcomingAvailabilityRequest(
  @QueryParam(commaSeparatedList = true) networkIds: Seq[Int] = Seq(),
  @QueryParam(commaSeparatedList = true) networks: Seq[String] = Seq(),
  @QueryParam days: Option[Int],
  @QueryParam fields: Option[String])
