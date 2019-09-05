package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  ExternalSource,
  PersonAssociationType,
  PersonThing,
  ThingLike,
  ThingRaw,
  ThingType
}
import com.teletracker.common.model.tmdb.{
  CastMember,
  MediaType,
  Person,
  PersonCredit
}
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import com.teletracker.common.util.Slug
import javax.inject.Inject
import com.teletracker.common.util.TheMovieDb._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ImportPeopleFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](storage, thingsDbAccess) {

  override protected def extraWork(
    thingLike: ThingLike,
    entity: Person
  ): Future[Unit] = {
    Future.unit
//    entity.combined_credits
//      .map(credits => {
//        credits.cast.map(_.id.toString) ++ credits.crew.map(_.id.toString)
//      })
//      .map(_.toSet)
//      .map(
//        thingsDbAccess
//          .findThingsByTmdbIds(ExternalSource.TheMovieDb, _, None)
//      )
//      .map(_.flatMap(thingByExternalId => {
//        val cast = entity.combined_credits
//          .map(_.cast)
//          .map(
//            _.flatMap(
//              credit =>
//                getThingForCredit(credit, thingByExternalId)
//                  .map(
//                    thing =>
//                      saveAssociations(
//                        thingLike.id,
//                        thing.id,
//                        PersonAssociationType.Cast,
//                        getCharacterName(entity, credit, thing)
//                      )
//                  )
//            )
//          )
//          .map(Future.sequence(_))
//          .map(_.map(_ => {}))
//          .getOrElse(Future.unit)
//
//        val crew = entity.combined_credits
//          .map(_.crew)
//          .map(_.filter(_.media_type.isDefined))
//          .map(
//            _.flatMap(
//              credit =>
//                getThingForCredit(credit, thingByExternalId)
//                  .map(
//                    thing =>
//                      saveAssociations(
//                        thingLike.id,
//                        thing.id,
//                        PersonAssociationType.Crew,
//                        None
//                      )
//                  )
//            )
//          )
//          .map(Future.sequence(_))
//          .map(_.map(_ => {}))
//          .getOrElse(Future.unit)
//
//        (for {
//          _ <- cast
//          _ <- crew
//        } yield {}).recover {
//          case NonFatal(e) =>
//            logger.error("Hit error during estra work", e)
//        }
//      }))
//      .getOrElse(Future.unit)
  }

  private def getThingForCredit(
    personCredit: PersonCredit,
    things: Map[(String, ThingType), ThingRaw]
  ): Option[ThingRaw] = {
    personCredit.media_type
      .flatMap {
        case MediaType.Movie =>
          things.get(personCredit.id.toString -> ThingType.Movie)
        case MediaType.Tv =>
          things.get(personCredit.id.toString -> ThingType.Show)
      }
      .orElse {
        personCredit.name.flatMap(name => {
          val both = List(
            things.get(personCredit.id.toString -> ThingType.Movie),
            things.get(personCredit.id.toString -> ThingType.Show)
          ).flatten
          val slug = personCredit.releaseYear.map(year => Slug(name, year))

          slug.flatMap(s => both.find(_.normalizedName == s)).orElse {
            both.find(_.name == name)
          }
        })
      }
  }

  import io.circe.optics.JsonPath._

  val movieCast =
    root.themoviedb.movie.credits.cast.as[Option[List[CastMember]]]
  val showCast = root.themoviedb.show.credits.cast.as[Option[List[CastMember]]]

  private def getCharacterName(
    person: Person,
    credit: PersonCredit,
    thingRaw: ThingRaw
  ) = {
    credit.character.orElse {
      thingRaw.metadata.flatMap(json => {
        Stream(movieCast, showCast)
          .flatMap(lens => {
            val found = lens.getOption(json).flatten
            found.flatMap(findMatchInCast(person, _))
          })
          .headOption
          .flatMap(c => c.character.orElse(c.character_name))
      })
    }
  }

  private def findMatchInCast(
    person: Person,
    members: List[CastMember]
  ) = {
    members.find(member => {
      member.name.isDefined && person.name.isDefined && member.name.get == person.name.get
    })
  }

  private def saveAssociations(
    personId: UUID,
    thingId: UUID,
    personAssociationType: PersonAssociationType,
    character: Option[String],
    order: Option[Int]
  ): Future[PersonThing] = {
    thingsDbAccess.upsertPersonThing(
      PersonThing(personId, thingId, personAssociationType, character, order)
    )
  }
}
