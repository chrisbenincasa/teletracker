package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.denorm.ItemCreditsDenormalizationHelper
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.PersonImportHandler.{
  PersonImportHandlerArgs,
  PersonImportResult,
  PersonInsertResult,
  PersonUpdateResult
}
import com.teletracker.common.pubsub.{TaskScheduler, TaskTag}
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.common.tasks.model.{
  DenormalizePersonTaskArgs,
  TeletrackerTaskIdentifier
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Slug
import com.teletracker.common.util.time.LocalDateUtils
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
    scheduleDenorm: Boolean,
    forceScheduleDenorm: Boolean = false)

  sealed trait PersonImportResult {
    def personId: UUID
    def isInsertOperation: Boolean
    def isUpdateOperation: Boolean
    def inserted: Boolean
    def updated: Boolean
    def castNeedsDenorm: Boolean
    def crewNeedsDenorm: Boolean
    def itemChanged: Boolean
    def jsonResult: Option[String]
  }

  case class PersonInsertResult(
    personId: UUID,
    inserted: Boolean,
    jsonResult: Option[String])
      extends PersonImportResult {
    override def isInsertOperation: Boolean = true
    override def isUpdateOperation: Boolean = false
    override def updated: Boolean = false
    override def castNeedsDenorm: Boolean = true
    override def crewNeedsDenorm: Boolean = true
    override def itemChanged: Boolean = true
  }

  case class PersonUpdateResult(
    personId: UUID,
    updated: Boolean,
    castNeedsDenorm: Boolean,
    crewNeedsDenorm: Boolean,
    itemChanged: Boolean,
    jsonResult: Option[String])
      extends PersonImportResult {
    override def isInsertOperation: Boolean = false
    override def isUpdateOperation: Boolean = true
    override def inserted: Boolean = false
  }
}

class PersonImportHandler @Inject()(
  personLookup: PersonLookup,
  personUpdater: PersonUpdater,
  itemLookup: ItemLookup,
  taskScheduler: TaskScheduler,
  creditsDenormalizer: ItemCreditsDenormalizationHelper
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
        case Success(result) =>
          val changeHappened = result.itemChanged || result.inserted || result.castNeedsDenorm || result.crewNeedsDenorm
          if (args.forceScheduleDenorm || (args.scheduleDenorm && changeHappened)) {
            logger.info(
              s"Scheduling denormalization task item id = ${result.personId}"
            )

            taskScheduler.schedule(
              TaskMessageHelper.forTaskArgs(
                TeletrackerTaskIdentifier.DENORMALIZE_PERSON_TASK,
                DenormalizePersonTaskArgs(
                  personId = result.personId,
                  dryRun = args.dryRun
                ),
                tags = Some(Set(TaskTag.RequiresTmdbApi))
              )
            )
          } else if (!args.scheduleDenorm && result.itemChanged) {
            Future.successful {
              logger.info(
                s"Would've scheduled denormalization task item id = ${result.personId}"
              )
            }
          } else {
            Future.unit
          }
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
      .flatMap(
        castAndCrewById => {
          val cast = buildCast(person, castAndCrewById)
          val castNeedsDenorm = creditsDenormalizer
            .personCastNeedsDenormalization(cast, existingPerson.cast_credits)
          val crew = buildCrew(person, castAndCrewById)
          val crewNeedsDenorm = creditsDenormalizer
            .personCrewNeedsDenormalization(crew, existingPerson.crew_credits)

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

          val updatedPerson = existingPerson.copy(
            adult = person.adult.orElse(person.adult),
            biography = person.biography.orElse(existingPerson.biography),
            birthday = person.birthday
              .filter(_.nonEmpty)
              .flatMap(LocalDateUtils.parseLocalDateWithFallback)
              .orElse(existingPerson.birthday),
            cast_credits = cast,
            crew_credits = crew,
            deathday = person.deathday
              .filter(_.nonEmpty)
              .flatMap(LocalDateUtils.parseLocalDateWithFallback)
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
                  .flatMap(LocalDateUtils.parseLocalDateWithFallback)
                  .map(_.getYear)
              )
            ),
            known_for = None
          )

          val personToUpdateFut =
            if (updatedPerson.slug != existingPerson.slug) {
              ensureUniqueSlug(updatedPerson)
            } else {
              Future.successful(updatedPerson)
            }

          personToUpdateFut.flatMap(personToUpdate => {
            if (args.dryRun) {
              Future.successful {
                logger.info(
                  s"Would've updated id = ${existingPerson.id}:\n${diff(existingPerson.asJson, personToUpdate.asJson).asJson.spaces2}"
                )

                PersonUpdateResult(
                  personId = personToUpdate.id,
                  updated = false,
                  castNeedsDenorm = castNeedsDenorm,
                  crewNeedsDenorm = crewNeedsDenorm,
                  itemChanged = existingPerson != personToUpdate,
                  jsonResult = Some(personUpdater.getUpdateJson(personToUpdate))
                )
              }
            } else if (personToUpdate != existingPerson) {
              logger.info(
                s"Updated existing person (id = ${personToUpdate.id}, slug = ${personToUpdate.slug})"
              )

              personUpdater
                .update(personToUpdate)
                .map(_ => {
                  PersonUpdateResult(
                    personId = personToUpdate.id,
                    updated = true,
                    castNeedsDenorm = castNeedsDenorm,
                    crewNeedsDenorm = crewNeedsDenorm,
                    itemChanged = true,
                    jsonResult = None
                  )
                })
            } else {
              Future.successful {
                logger.info(
                  s"Person id = ${personToUpdate.id} hasn't changed. Skipping update."
                )
                PersonUpdateResult(
                  personId = personToUpdate.id,
                  updated = false,
                  castNeedsDenorm = false,
                  crewNeedsDenorm = false,
                  itemChanged = false,
                  jsonResult = None
                )
              }
            }
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
          birthday = person.birthday
            .filter(_.nonEmpty)
            .flatMap(LocalDateUtils.parseLocalDateWithFallback),
          cast_credits = cast,
          crew_credits = crew,
          external_ids = Some(toEsItem.esExternalId(person).toList),
          deathday = person.deathday
            .filter(_.nonEmpty)
            .flatMap(LocalDateUtils.parseLocalDateWithFallback),
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
                .flatMap(LocalDateUtils.parseLocalDateWithFallback)
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
                s"Would've inserted new person (id = ${person.id}, slug = ${person.slug})\n:${person.asJson.spaces2}"
              )

              PersonInsertResult(
                personId = person.id,
                inserted = false,
                jsonResult = Some(personUpdater.getIndexJson(person))
              )
            }
          } else {
            logger.info(
              s"Inserted new person (id = ${person.id}, slug = ${person.slug})"
            )
            personUpdater
              .insert(person)
              .map(_ => {
                PersonInsertResult(
                  personId = person.id,
                  inserted = true,
                  jsonResult = None
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
