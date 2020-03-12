package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.{
  EsExternalId,
  EsItem,
  EsPerson,
  EsPersonCastCredit,
  EsPersonCrewCredit,
  ItemLookup,
  ItemUpdater,
  PersonLookup,
  PersonUpdater
}
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{
  CastMember,
  MediaType,
  Person,
  PersonCredit
}
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
  genreCache: GenreCache,
  personLookup: PersonLookup,
  itemSearch: ItemLookup,
  personUpdater: PersonUpdater
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](
      s3,
      sourceRetriever,
      genreCache
    ) {

  implicit def toEsItem: ToEsItem[Person] = ToEsItem.forTmdbPerson

  override protected def shouldHandleItem(item: Person): Boolean =
    item.name.exists(_.nonEmpty)

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    person: Person
  ): Future[Unit] = {
    personLookup
      .lookupPersonByExternalId(ExternalSource.TheMovieDb, person.id.toString)
      .map {
        case Some(existingPerson) =>
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
                  popularity =
                    person.popularity.orElse(existingPerson.popularity),
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
            .flatMap(ensureUniqueSlug)
            .flatMap(personUpdater.update)

        case None =>
          fetchCastAndCredits(person)
            .map(castAndCrewById => {
              val cast = buildCast(person, castAndCrewById)
              val crew = buildCrew(person, castAndCrewById)

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
            .flatMap(ensureUniqueSlug)
            .flatMap(personUpdater.insert)
      }
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
            val nextSlugIndex = foundPeople
              .flatMap(_.slug)
              .map(
                _.value.replaceAllLiterally(
                  esPerson.slug.get.value,
                  ""
                )
              )
              .map(_.toInt)
              .max + 1

            esPerson.copy(
              slug = Some(
                esPerson.slug.get.addSuffix(s"$nextSlugIndex")
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

        itemSearch.lookupItemsByExternalIds(lookupTriples)
      })
      .getOrElse(
        Future.successful(Map.empty[(ExternalSource, String), EsItem])
      )
  }
}
