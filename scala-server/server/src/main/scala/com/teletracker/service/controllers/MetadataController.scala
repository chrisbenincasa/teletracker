package com.teletracker.service.controllers

import com.teletracker.service.db.access.{NetworksDbAccess, ThingsDbAccess}
import com.teletracker.service.db.model.GenreType
import com.teletracker.service.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class MetadataController @Inject()(
  thingsDbAccess: ThingsDbAccess,
  networksDbAccess: NetworksDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  get("/api/v1/genres/?") { req: GetAllGenresRequest =>
    val parsedType =
      req.`type`.flatMap(t => Try(GenreType.fromString(t)).toOption)
    thingsDbAccess.getAllGenres(parsedType).map(DataResponse(_))
  }

  get("/api/v1/networks/?") { _: Request =>
    networksDbAccess
      .findAllNetworks()
      .map(_.map(_._2).sortBy(_.name))
      .map(DataResponse(_))
  }
}

case class GetAllGenresRequest(@QueryParam `type`: Option[String])
