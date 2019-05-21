package com.teletracker.service.controllers

import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.db.ThingsDbAccess
import com.teletracker.service.model.DataResponse
import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.controllers.utils.CanParseFieldFilter
import com.teletracker.service.util.HasFieldsFilter
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
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
  @RouteParam id: Int,
  @QueryParam fields: Option[String],
  request: Request)
    extends HasFieldsFilter
