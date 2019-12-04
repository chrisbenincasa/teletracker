package com.teletracker.service.controllers

import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.{NetworksDbAccess}
import com.teletracker.common.db.model.GenreType
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{GenreCache, NetworkCache}
import com.teletracker.service.api.model
import com.teletracker.service.api.model.{Genre, Network}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class MetadataController @Inject()(
  baseDbProvider: BaseDbProvider,
  networksDbAccess: NetworksDbAccess,
  genreCache: GenreCache,
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    get("/metadata") { req: Request =>
      val genresFut = genreCache.get()
      val networksFut = networkCache.getAllNetworks()

      for {
        genres <- genresFut
        networks <- networksFut
      } yield {
        DataResponse(
          model.MetadataResponse(
            genres
              .groupBy(_.id)
              .values
              .flatMap(_.headOption)
              .toList
              .map(Genre.fromStoredGenre),
            networks
              .groupBy(_.id)
              .values
              .flatMap(_.headOption)
              .toList
              .map(Network.fromStoredNetwork)
          )
        )
      }
    }

    get("/genres/?") { req: GetAllGenresRequest =>
      val parsedType =
        req.`type`.flatMap(t => Try(GenreType.fromString(t)).toOption)
      genreCache
        .get()
        .map(_.filter(g => parsedType.forall(g.genreTypes.contains)))
        .map(_.map(Genre.fromStoredGenre))
        .map(DataResponse(_))
    }

    get("/networks/?") { _: Request =>
      networkCache
        .getAllNetworks()
        .map(_.sortBy(_.name))
        .map(_.map(Network.fromStoredNetwork))
        .map(DataResponse(_))
    }
  }
}

case class GetAllGenresRequest(@QueryParam `type`: Option[String])
