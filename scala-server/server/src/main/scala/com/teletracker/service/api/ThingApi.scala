package com.teletracker.service.api

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  PartialThing,
  Person,
  ThingCastMember,
  ThingType
}
import com.teletracker.common.model.tmdb.CastMember
import com.teletracker.service.util.HasThingIdOrSlug
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ThingApi @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {
  import io.circe.optics.JsonPath._

  private val movieCast =
    root.themoviedb.movie.credits.cast.as[Option[List[CastMember]]]
  private val showCast =
    root.themoviedb.show.credits.cast.as[Option[List[CastMember]]]

  def getThing(
    userId: String,
    idOrSlug: String,
    thingType: ThingType
  ): Future[Option[PartialThing]] = {
    val thingFut = HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id)    => thingsDbAccess.findThingById(id, thingType)
      case Right(slug) => thingsDbAccess.findThingBySlug(slug, thingType)
    }

    thingFut.flatMap {
      case None =>
        Future.successful(None)

      case Some(thing) =>
        val userThingDetailsFut = thingsDbAccess
          .getThingUserDetails(userId, thing.id)

        val relevantPeopleFut =
          thingsDbAccess.findPeopleForThing(thing.id, None)

        val rawJsonMembers = thing.metadata
          .flatMap(meta => {
            movieCast
              .getOption(meta)
              .orElse(showCast.getOption(meta))
              .flatten
          })

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
                person.tmdbId,
                person.popularity
              )
          }

          Some(
            thing
              .withUserMetadata(userThingDetails)
              .withCast(sortCastMembers(rawJsonMembers, cast.toList))
          )
        }
    }
  }

  private def sortCastMembers(
    rawJsonMembers: Option[List[CastMember]],
    thingCastMember: List[ThingCastMember]
  ): List[ThingCastMember] = {
    rawJsonMembers match {
      case None => thingCastMember
      case Some(rawMembers) => {
        val orderById = rawMembers
          .map(
            member => member.id.toString -> member.order.getOrElse(Int.MaxValue)
          )
          .toMap

        thingCastMember
          .map(member => {
            member.withOrder(member.tmdbId.flatMap(orderById.get))
          })
          .sortWith {
            case (left, _) if left.order.isEmpty   => false
            case (_, right) if right.order.isEmpty => true
            case (left, right)                     => left.order.get < right.order.get
          }
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
