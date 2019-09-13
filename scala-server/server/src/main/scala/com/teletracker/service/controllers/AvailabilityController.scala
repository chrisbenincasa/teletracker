package com.teletracker.service.controllers

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.elasticsearch.ItemAvailabilitySearch
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.{CanParseFieldFilter, NetworkCache}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api.model.Item
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import io.circe.generic.auto._
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AvailabilityController @Inject()(
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache,
  itemAvailabilitySearch: ItemAvailabilitySearch
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {

  prefix("/api/v1/availability") {
    get("/new") { req: UpcomingAvailabilityRequest =>
      thingsDbAccess
        .findPastAvailability(
          req.days.getOrElse(30),
          Some(req.networkIds.toSet)
        )
        .map(avs => {
          DataResponse.complex(avs)
        })
    }

    get("/upcoming") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      thingsDbAccess
        .findFutureAvailability(
          req.days.getOrElse(30),
          Some(req.networkIds.toSet),
          selectFields
        )
        .map(avs => {
          response.ok.contentTypeJson().body(DataResponse.complex(avs))
        })
    }

    get("/all") { req: UpcomingAvailabilityRequest =>
      val selectFields = parseFieldsOrNone(req.fields)

      val networkIdsFut = if (req.networkIds.nonEmpty) {
        Future.successful(Some(req.networkIds.toSet))
      } else if (req.networks.nonEmpty) {
        networkCache
          .get()
          .map(
            _.values
              .filter(network => {
                req.networks.contains(network.slug.value)
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
