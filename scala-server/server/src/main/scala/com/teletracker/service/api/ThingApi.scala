package com.teletracker.service.api

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  PartialThing,
  Person,
  ThingCastMember,
  ThingType
}
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
        val userThingDetailsFut = thingsDbAccess
          .getThingUserDetails(userId, movie.id)

        val relevantPeopleFut =
          thingsDbAccess.findPeopleForThing(movie.id, None)

        for {
          userThingDetails <- userThingDetailsFut
          relevantPeople <- relevantPeopleFut
        } yield {
          val cast = relevantPeople.map {
            case (person, relation) =>
              ThingCastMember(
                person.id,
                person.normalizedName,
                relation.characterName,
                Some(relation.relationType),
                person.tmdbId
              )
          }

          Some(movie.withUserMetadata(userThingDetails).withCast(cast.toList))
        }
    }
  }

  def getPerson(
    userId: String,
    idOrSlug: String
  ): Future[Option[Person]] = {
    HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id)    => thingsDbAccess.findPersonById(id)
      case Right(slug) => thingsDbAccess.findPersonBySlug(slug)
    }
  }
}
