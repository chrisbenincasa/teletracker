package com.teletracker.service.api

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  PartialThing,
  Person,
  ThingCastMember,
  ThingRaw,
  ThingType
}
import com.teletracker.common.model.tmdb.CastMember
import com.teletracker.service.api.model.{
  Converters,
  EnrichedPerson,
  PersonCredit
}
import com.teletracker.service.util.HasThingIdOrSlug
import io.circe.Json
import javax.inject.Inject
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.math.Ordering.OptionOrdering

class ThingApi @Inject()(
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext) {
  import io.circe.optics.JsonPath._

  private val movieCast =
    root.themoviedb.movie.credits.cast.as[Option[List[CastMember]]]
  private val showCast =
    root.themoviedb.show.credits.cast.as[Option[List[CastMember]]]

  private val movieReleaseDate =
    root.themoviedb.movie.release_date.as[String]

  private val tvReleaseDate =
    root.themoviedb.show.first_air_date.as[String]

  private val posterPaths =
    Stream("movie", "show").map(tpe => {
      (j: Json) =>
        j.hcursor
          .downField("themoviedb")
          .downField(tpe)
          .get[Option[String]]("poster_path")
          .toOption
          .flatten
    })

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

  implicit private val LocalDateOrdering: Ordering[LocalDate] =
    Ordering.fromLessThan(_.isBefore(_))

  private def NullsLastOrdering[T](
    implicit ord: Ordering[T]
  ): Ordering[Option[T]] = new OptionOrdering[T] {
    override def optionOrdering: Ordering[T] = ord
    override def compare(
      x: Option[T],
      y: Option[T]
    ) = (x, y) match {
      case (None, None)       => 0
      case (None, _)          => 1
      case (_, None)          => -1
      case (Some(x), Some(y)) => optionOrdering.compare(x, y)
    }
  }

  def getPerson(
    userId: String,
    idOrSlug: String
  ): Future[Option[EnrichedPerson]] = {
    val personFut = HasThingIdOrSlug.parse(idOrSlug) match {
      case Left(id)    => thingsDbAccess.findPersonById(id)
      case Right(slug) => thingsDbAccess.findPersonBySlug(slug)
    }

    personFut.flatMap {
      case None => Future.successful(None)

      case Some(person) =>
        val relevantThingsFut =
          thingsDbAccess.findThingsForPerson(person.id, None)

        relevantThingsFut.map(relevantThings => {
          val credits = relevantThings.map {
            case (thing, relation) =>
              PersonCredit(
                id = thing.id,
                name = thing.name,
                normalizedName = thing.normalizedName,
                tmdbId = thing.tmdbId,
                popularity = thing.popularity,
                `type` = thing.`type`,
                associationType = relation.relationType,
                characterName = relation.characterName,
                releaseDate = extractReleaseDate(thing),
                posterPath = extractPosterPath(thing)
              )
          }

          Some(
            Converters
              .dbPersonToEnrichedPerson(person)
              .withCredits(
                credits.toList
                  .sortBy(_.releaseDate)(NullsLastOrdering[LocalDate].reverse)
              )
          )
        })
    }
  }

  private def extractReleaseDate(thingRaw: ThingRaw) = {
    thingRaw.metadata
      .flatMap(meta => {
        movieReleaseDate.getOption(meta).orElse(tvReleaseDate.getOption(meta))
      })
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_))
  }

  private def extractPosterPath(thingRaw: ThingRaw) = {
    thingRaw.metadata.collectFirst {
      case meta => posterPaths.map(_.apply(meta)).find(_.isDefined).flatten
    }.flatten
  }
}
