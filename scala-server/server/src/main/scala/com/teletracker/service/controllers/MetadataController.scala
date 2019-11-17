package com.teletracker.service.controllers

import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.{NetworksDbAccess, ThingsDbAccess}
import com.teletracker.common.db.model.{Genre, GenreType, Network}
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{GenreCache, NetworkCache}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.request.QueryParam
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Try

class MetadataController @Inject()(
  baseDbProvider: BaseDbProvider,
  thingsDbAccess: ThingsDbAccess,
  networksDbAccess: NetworksDbAccess,
  genreCache: GenreCache,
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    get("/metadata") { req: Request =>
      val genresFut = genreCache.get()
      val networksFut = networkCache.get()

      for {
        genres <- genresFut
        networks <- networksFut
      } yield {
        DataResponse(
          MetadataResponse(
            genres.values.groupBy(_.id.get).values.flatMap(_.headOption).toList,
            networks.values
              .groupBy(_.id.get)
              .values
              .flatMap(_.headOption)
              .toList
          )
        )
      }
    }

    get("/genres/?") { req: GetAllGenresRequest =>
      val parsedType =
        req.`type`.flatMap(t => Try(GenreType.fromString(t)).toOption)
      thingsDbAccess
        .getAllGenres(parsedType)
        .map(DataResponse(_))
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

case class MetadataResponse(
  genres: List[Genre],
  networks: List[Network])
