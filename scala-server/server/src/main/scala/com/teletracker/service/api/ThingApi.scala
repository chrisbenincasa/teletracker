package com.teletracker.service.api

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{PartialThing, ThingType}
import com.teletracker.service.util.HasThingIdOrSlug
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ThingApi @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {
  def getThing(
    userId: String,
    idOrSlug: String,
    thingType: ThingType
  ): Future[Option[PartialThing]] = {
    val movieFut = HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id)    => thingsDbAccess.findThingById(id, thingType)
      case Right(slug) => thingsDbAccess.findThingBySlug(slug, thingType)
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
