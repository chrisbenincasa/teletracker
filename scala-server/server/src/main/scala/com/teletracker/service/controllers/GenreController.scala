package com.teletracker.service.controllers

import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api.ThingApi
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.RouteParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class GenreController @Inject()(
  thingApi: ThingApi
)(implicit executionContext: ExecutionContext)
    extends Controller {

  prefix("/api/v1/genres") {
    get("/:idOrSlug") { req: PopularForGenreRequest =>
      thingApi.getPopularByGenre(req.idOrSlug).map {
        case None         => response.notFound
        case Some(things) => DataResponse.complex(things)
      }
    }
  }
}

case class PopularForGenreRequest(@RouteParam idOrSlug: String)
