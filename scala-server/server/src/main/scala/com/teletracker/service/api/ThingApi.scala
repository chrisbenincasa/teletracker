package com.teletracker.service.api

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{PartialThing, ThingType}
import com.teletracker.common.util.Slug
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object ThingApi {
  final private val UuidRegex =
    "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}".r
}

class ThingApi @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {
  def getThing(
    userId: String,
    idOrSlug: String,
    thingType: ThingType
  ): Future[Option[PartialThing]] = {
    val movieFut =
      if (ThingApi.UuidRegex.findFirstIn(idOrSlug).isDefined) {
        val id = UUID.fromString(idOrSlug)
        thingsDbAccess.findThingById(id, thingType)
      } else {
        thingsDbAccess.findThingBySlug(Slug.raw(idOrSlug), thingType)
      }

    movieFut.flatMap {
      case None =>
        Future.successful(None)

      case Some(movie) =>
        thingsDbAccess
          .getThingUserDetails(userId, movie.id)
          .map(movie.withUserMetadata)
          .map(Some(_))
    }
  }
}
