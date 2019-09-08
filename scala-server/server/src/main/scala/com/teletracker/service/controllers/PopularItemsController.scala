package com.teletracker.service.controllers

import com.teletracker.common.db.access.{
  SyncThingsDbAccess,
  ThingsDbAccess,
  UserThingDetails
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.DataResponse
import com.teletracker.common.util.json.circe._
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.cache.PopularItemsCache
import com.teletracker.service.controllers.TeletrackerController._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PopularItemsController @Inject()(
  popularItemsCache: PopularItemsCache,
  tmdbClient: TmdbClient,
  thingsDbAccess: SyncThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    filter[JwtAuthFilter] {
      get("/popular") { req: Request =>
        for {
          popularItems <- popularItemsCache
            .getOrSet()
          thingIds = popularItems.map(_.id)

          thingUserDetails <- thingsDbAccess
            .getThingsUserDetails(req.authenticatedUserId, thingIds.toSet)
        } yield {
          val itemsWithMeta = popularItems.map(thing => {
            val meta = thingUserDetails
              .getOrElse(thing.id, UserThingDetails.empty)
            thing.withUserMetadata(meta)
          })

          DataResponse.complex(itemsWithMeta)
        }
      }
    }
  }
}
