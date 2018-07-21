package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MovieController @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1/movies") {
    get("/:id") { req: GetMovieRequest =>
      thingsDbAccess.findMovieById(req.id).map(result => {
        if (result.isEmpty) {
          response.status(404)
        } else {
          response.ok.contentTypeJson().body(DataResponse.complex(result.get))
        }
      })
    }
  }
}

case class GetMovieRequest(@RouteParam id: Int)