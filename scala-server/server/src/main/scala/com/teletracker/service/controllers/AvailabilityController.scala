package com.teletracker.service.controllers

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.{CanParseFieldFilter, NetworkCache}
import com.teletracker.common.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import io.circe.generic.auto._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AvailabilityController @Inject()(
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache
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

      val networkIdsFut = if (req.networkIds.exists(_.nonEmpty)) {
        Future.successful(req.networkIds)
      } else if (req.networks.exists(_.nonEmpty)) {
        networkCache
          .get()
          .map(
            _.values
              .filter(network => {
                req.networks.get.contains(network.slug.value)
              })
              .flatMap(_.id)
              .toSet
          )
          .map(Some(_))
      } else {
        Future.successful(None)
      }

      networkIdsFut.flatMap(networkIds => {
        thingsDbAccess
          .findRecentAvailability(
            req.days.getOrElse(30),
            networkIds,
            selectFields
          )
          .map(avs => {
            response.ok.contentTypeJson().body(DataResponse.complex(avs))
          })
      })
    }
  }
}

case class UpcomingAvailabilityRequest(
  @QueryParam(commaSeparatedList = true) networkIds: Option[Set[Int]],
  @QueryParam(commaSeparatedList = true) networks: Option[Set[String]],
  @QueryParam days: Option[Int],
  @QueryParam fields: Option[String])
