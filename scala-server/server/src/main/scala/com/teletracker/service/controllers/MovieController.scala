package com.teletracker.service.controllers

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{CanParseFieldFilter, HasFieldsFilter}
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.auth.RequestContext._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext

class MovieController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller
    with CanParseFieldFilter {
  prefix("/api/v1/movies") {
    filter[JwtAuthFilter].apply {
      get("/:id") { req: GetMovieRequest =>
        val userId = req.request.authContext.user.id
        val thingDetailsFut = thingsDbAccess.getThingUserDetails(userId, req.id)

        val movieByIdFut = thingsDbAccess.findMovieById(req.id)

        for {
          thingDetails <- thingDetailsFut
          movieById <- movieByIdFut
        } yield {
          if (movieById.isEmpty) {
            response.status(404)
          } else {
            val finalResult = movieById.get.withUserMetadata(thingDetails)
            response.ok
              .contentTypeJson()
              .body(DataResponse.complex(finalResult))
          }
        }
      }
    }
  }
}

case class GetMovieRequest(
  @RouteParam id: UUID,
  @QueryParam fields: Option[String],
  request: Request)
    extends HasFieldsFilter
