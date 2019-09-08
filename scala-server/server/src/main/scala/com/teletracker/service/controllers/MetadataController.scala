package com.teletracker.service.controllers

import com.teletracker.common.db.access.{
  NetworksDbAccess,
  SyncThingsDbAccess,
  ThingsDbAccess
}
import com.teletracker.common.db.model.GenreType
import com.teletracker.common.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class MetadataController @Inject()(
  thingsDbAccess: SyncThingsDbAccess,
  networksDbAccess: NetworksDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    get("/genres/?") { req: GetAllGenresRequest =>
      val parsedType =
        req.`type`.flatMap(t => Try(GenreType.fromString(t)).toOption)
      thingsDbAccess.getAllGenres(parsedType).map(DataResponse(_))
    }

    get("/networks/?") { _: Request =>
      networksDbAccess
        .findAllNetworks()
        .map(_.map(_._2).sortBy(_.name))
        .map(DataResponse(_))
    }
  }
}

case class GetAllGenresRequest(@QueryParam `type`: Option[String])
