package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch._
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.PersonImportHandler.{
  PersonImportHandlerArgs,
  PersonImportResult
}
import com.teletracker.common.pubsub.TaskScheduler
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.common.tasks.model.{
  DenormalizePersonTaskArgs,
  TeletrackerTaskIdentifier
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Slug
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NonFatal

object PersonImportHandler {
  case class PersonImportHandlerArgs(
    dryRun: Boolean,
    scheduleDenorm: Boolean)

  case class PersonImportResult(
    personId: UUID,
    inserted: Boolean,
    updated: Boolean,
    castNeedsDenorm: Boolean,
    crewNeedsDenorm: Boolean,
    itemChanged: Boolean)
}

class PersonImportHandler @Inject()(
  personLookup: PersonLookup,
  personUpdater: PersonUpdater,
  itemLookup: ItemLookup,
  taskScheduler: TaskScheduler
)(implicit executionContext: ExecutionContext) {
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private def toEsItem: ToEsItem[Person] = ToEsItem.forTmdbPerson

  def handleItem(
    args: PersonImportHandlerArgs,
    person: Person
  ): Future[PersonImportResult] = {
    personLookup
      .lookupPersonByExternalId(ExternalSource.TheMovieDb, person.id.toString)
      .flatMap {
        case Some(existingPerson) =>
          handlePersonUpdate(person, existingPerson, args)

        case None =>
          handlePersonInsert(person, args)
      }
      .through {
        case Success(result) if args.scheduleDenorm =>
          logger.debug(
            s"Scheduling denormalization task item id = ${result.personId}"
          )

          taskScheduler.schedule(
            TaskMessageHelper.forTaskArgs(
              TeletrackerTaskIdentifier.DENORMALIZE_PERSON_TASK,
              DenormalizePersonTaskArgs(
                personId = result.personId,
                dryRun = args.dryRun
              ),
              tags = None
            )
          )
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(e.getMessage)
          throw e
      }
  }

  private def handlePersonUpdate(
    person: Person,
    existingPerson: EsPerson,
    args: PersonImportHandlerArgs
  ): Future[PersonImportResult] = {
    fetchCastAndCredits(person)
      .map(
        castAndCrewById => {
          val cast = buildCast(person, castAndCrewById)
          val crew = buildCrew(person, castAndCrewById)

          val images =
            toEsItem
              .esItemImages(person)
              .foldLeft(existingPerson.imagesGrouped)((acc, image) => {
                val externalSource =
                  ExternalSource.fromString(image.provider_shortname)
                acc.updated((externalSource, image.image_type), image)
              })
              .values

          val newName = person.name.orElse(existingPerson.name)

          existingPerson.copy(
            adult = person.adult.orElse(person.adult),
            biography = person.biography.orElse(existingPerson.biography),
            birthday = person.birthday
              .filter(_.nonEmpty)
              .map(LocalDate.parse(_))
              .orElse(existingPerson.birthday),
            cast_credits = cast,
            crew_credits = crew,
            deathday = person.deathday
              .filter(_.nonEmpty)
              .map(LocalDate.parse(_))
              .orElse(existingPerson.deathday),
            homepage = person.homepage.orElse(existingPerson.homepage),
            images = Some(images.toList),
            name = newName,
            place_of_birth =
              person.place_of_birth.orElse(existingPerson.place_of_birth),
            popularity = person.popularity.orElse(existingPerson.popularity),
            slug = Some(
              Slug(
                newName.get,
                person.birthday
                  .filter(_.nonEmpty)
                  .map(LocalDate.parse(_))
                  .map(_.getYear)
              )
            ),
            known_for = None
          )
        }
      )
      .flatMap(updatedPerson => {
        if (updatedPerson.slug != existingPerson.slug) {
          ensureUniqueSlug(updatedPerson)
        } else {
          Future.successful(updatedPerson)
        }
      })
      .flatMap(
        person =>
          if (args.dryRun) {
            Future.successful {
              logger.info(
                s"Would've updated id = ${existingPerson.id}:\n${diff(existingPerson.asJson, person.asJson).asJson.spaces2}"
              )

              PersonImportResult(
                personId = person.id,
                inserted = false,
                updated = false,
                castNeedsDenorm = false,
                crewNeedsDenorm = false,
                itemChanged = existingPerson != person
              )
            }
          } else {
            personUpdater
              .update(person)
              .map(_ => {
                PersonImportResult(
                  personId = person.id,
                  inserted = false,
                  updated = true,
                  castNeedsDenorm = false,
                  crewNeedsDenorm = false,
                  itemChanged = true
                )
              })
          }
      )
  }

  private def handlePersonInsert(
    person: Person,
    args: PersonImportHandlerArgs
  ) = {
    fetchCastAndCredits(person)
      .map(castAndCrewById => {
        val cast = buildCast(person, castAndCrewById)
        val crew = buildCrew(person, castAndCrewById)

        EsPerson(
          adult = person.adult,
          biography = person.biography,
          birthday = person.birthday.filter(_.nonEmpty).map(LocalDate.parse(_)),
          cast_credits = cast,
          crew_credits = crew,
          external_ids = Some(toEsItem.esExternalId(person).toList),
          deathday = person.deathday.filter(_.nonEmpty).map(LocalDate.parse(_)),
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
      .flatMap(ensureUniqueSlug)
      .flatMap(
        person => {
          if (args.dryRun) {
            Future.successful {
              logger.info(
                s"Would've inserted new movie (id = ${person.id}, slug = ${person.slug})\n:${person.asJson.spaces2}"
              )

              PersonImportResult(
                personId = person.id,
                inserted = false,
                updated = false,
                castNeedsDenorm = false,
                crewNeedsDenorm = false,
                itemChanged = true
              )
            }
          } else {
            personUpdater
              .insert(person)
              .map(_ => {
                PersonImportResult(
                  personId = person.id,
                  inserted = true,
                  updated = false,
                  castNeedsDenorm = false,
                  crewNeedsDenorm = false,
                  itemChanged = true
                )
              })
          }
        }
      )
  }

  private def buildCast(
    person: Person,
    castAndCrewById: Map[(ExternalSource, String), EsItem]
  ): Option[List[EsPersonCastCredit]] = {
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
  }

  private def buildCrew(
    person: Person,
    castAndCrewById: Map[(ExternalSource, String), EsItem]
  ): Option[List[EsPersonCrewCredit]] = {
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
  }

  private def ensureUniqueSlug(esPerson: EsPerson): Future[EsPerson] = {
    if (esPerson.slug.isDefined) {
      personLookup
        .lookupPeopleBySlugPrefix(esPerson.slug.get)
        .map(_.items)
        .map {
          case Nil =>
            esPerson

          case foundPeople =>
            esPerson.copy(
              slug = Some(
                Slug
                  .findNext(esPerson.slug.get, foundPeople.flatMap(_.slug))
                  .get
              )
            )
        }
    } else {
      Future.successful(esPerson)
    }
  }

  private def fetchCastAndCredits(
    person: Person
  ): Future[Map[(ExternalSource, String), EsItem]] = {
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

        itemLookup.lookupItemsByExternalIds(lookupTriples)
      })
      .getOrElse(
        Future.successful(Map.empty[(ExternalSource, String), EsItem])
      )
  }
}