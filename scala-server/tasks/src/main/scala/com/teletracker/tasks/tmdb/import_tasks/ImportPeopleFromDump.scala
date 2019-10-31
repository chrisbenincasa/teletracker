package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.{
  EsItem,
  EsPerson,
  EsPersonCastCredit,
  EsPersonCrewCredit,
  ItemSearch,
  ItemUpdater,
  PersonLookup
}
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{
  CastMember,
  MediaType,
  Person,
  PersonCredit
}
import com.teletracker.common.process.tmdb.TmdbSynchronousProcessor
import com.teletracker.common.util.{GenreCache, Slug}
import com.teletracker.common.util.TheMovieDb._
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ImportPeopleFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor,
  genreCache: GenreCache,
  personLookup: PersonLookup,
  itemSearch: ItemSearch
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](
      s3,
      sourceRetriever,
      thingsDbAccess,
      genreCache
    )
    with ImportTmdbDumpTaskToElasticsearch[Person] {

  implicit override def toEsItem: ToEsItem[Person] = ToEsItem.forTmdbPerson

  override protected def shouldHandleItem(item: Person): Boolean =
    item.name.exists(_.nonEmpty)

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    person: Person
  ): Future[Unit] = {
    personLookup
      .lookupPersonByExternalId(ExternalSource.TheMovieDb, person.id.toString)
      .map {
        case Some(value) =>
        case None =>
          person.combined_credits
            .map(credits => {
              val castIds = credits.cast.flatMap(castMember => {
                castMember.media_type.map(typ => {
                  castMember.id.toString -> typ.toThingType
                })
              })

              val crewIds = credits.crew.flatMap(crewMember => {
                crewMember.media_type.map(typ => {
                  crewMember.id.toString -> typ.toThingType
                })
              })

              val lookupTriples = (castIds ++ crewIds).map {
                case (id, typ) => (ExternalSource.TheMovieDb, id, typ)
              }

              itemSearch.lookupItemsByExternalIds(lookupTriples)
            })
            .getOrElse(
              Future.successful(Map.empty[(ExternalSource, String), EsItem])
            )
            .map(castAndCrewById => {
              val cast =
                person.combined_credits.map(_.cast.flatMap(castCredit => {
                  castAndCrewById
                    .get(ExternalSource.TheMovieDb -> castCredit.id.toString)
                    .filter(
                      matchingItem =>
                        castCredit.media_type
                          .map(_.toThingType)
                          .contains(matchingItem.`type`)
                    )
                    .map(matchingItem => {
                      EsPersonCastCredit(
                        id = matchingItem.id,
                        title = matchingItem.original_title.getOrElse(""),
                        character = castCredit.character,
                        `type` = matchingItem.`type`,
                        slug = matchingItem.slug
                      )
                    })
                }))

              val crew =
                person.combined_credits.map(_.crew.flatMap(crewCredit => {
                  castAndCrewById
                    .get(ExternalSource.TheMovieDb -> crewCredit.id.toString)
                    .filter(
                      matchingItem =>
                        crewCredit.media_type
                          .map(_.toThingType)
                          .contains(matchingItem.`type`)
                    )
                    .map(matchingItem => {
                      EsPersonCrewCredit(
                        id = matchingItem.id,
                        title = matchingItem.original_title.getOrElse(""),
                        department = crewCredit.department,
                        job = crewCredit.job,
                        `type` = matchingItem.`type`,
                        slug = matchingItem.slug
                      )
                    })
                }))

              EsPerson(
                adult = person.adult,
                biography = person.biography,
                birthday =
                  person.birthday.filter(_.nonEmpty).map(LocalDate.parse(_)),
                cast_credits = cast,
                crew_credits = crew,
                external_ids = Some(toEsItem.esExternalId(person).toList),
                deathday =
                  person.deathday.filter(_.nonEmpty).map(LocalDate.parse(_)),
                homepage = person.homepage,
                id = UUID.randomUUID(),
                images = Some(toEsItem.esItemImages(person)),
                name = person.name,
                place_of_birth = person.place_of_birth,
                popularity = person.popularity,
                slug = Some(
                  Slug(
                    person.name.get,
                    person.birthday
                      .filter(_.nonEmpty)
                      .map(LocalDate.parse(_))
                      .map(_.getYear)
                  )
                ),
                known_for = None
              )
            })

      }
  }

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
    order: Option[Int],
    department: Option[String],
    job: Option[String]
  ): Future[PersonThing] = {
    thingsDbAccess.upsertPersonThing(
      PersonThing(
        personId,
        thingId,
        personAssociationType,
        character,
        order,
        department,
        job
      )
    )
  }
}
