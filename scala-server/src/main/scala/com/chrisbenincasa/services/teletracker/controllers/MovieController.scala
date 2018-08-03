package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.auth.JwtAuthFilter
import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.auth.RequestContext._
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MovieController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
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
            response.ok.contentTypeJson().body(DataResponse.complex(finalResult))
          }
        }
      }
    }
  }
}

case class GetMovieRequest(
  @RouteParam id: Int,
  request: Request
)