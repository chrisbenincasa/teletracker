package com.teletracker.service.controllers

import com.teletracker.common.db.Bookmark
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.model.{DataResponse, Paging}
import com.teletracker.common.util.json.circe._
import com.teletracker.service.api.ThingApi
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{Max, Min}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class GenreController @Inject()(
  thingApi: ThingApi
)(implicit executionContext: ExecutionContext)
    extends Controller {

  prefix("/api/v1/genres") {
    get("/:idOrSlug") { req: PopularForGenreRequest =>
      thingApi
        .getPopularByGenre(
          req.idOrSlug,
          req.thingType,
          req.limit,
          req.bookmark.map(Bookmark.parse)
        )
        .map {
          case None => response.notFound
          case Some((things, bookmark)) =>
            DataResponse.forDataResponse(
              DataResponse(things, Some(Paging(bookmark.map(_.asString))))
            )
        }
    }
  }
}

case class PopularForGenreRequest(
  @RouteParam idOrSlug: String,
  @QueryParam thingType: Option[ThingType],
  @QueryParam @Max(50) @Min(0) limit: Int = 20,
  @QueryParam bookmark: Option[String])
