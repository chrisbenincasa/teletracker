package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{
  ExternalSource,
  Person,
  PersonAssociationType,
  ThingType
}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Slug
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.twitter.util.StorageUnit
import io.circe.syntax._
import javax.inject.Inject
import shapeless.{Inl, Inr}
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

class ImportPeopleToElasticsearch @Inject()(
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val peopleDump = args.value[URI]("peopleDump").get
    val thingInput = args.value[URI]("thingMapping").get
    val personThingMap = args.value[URI]("personThingMapping").get
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val outputPath =
      args.valueOrDefault("outputPath", System.getProperty("user.dir"))

    val fileRotator =
      new FileRotator(
        "people_output",
        StorageUnit.fromMegabytes(90),
        Some(outputPath)
      )

    val src = Source.fromURI(peopleDump)

    println("Creating tmdb ID to thing ID mapping")
    val tmpSrc = Source.fromURI(thingInput)
    val thingIdToDetails = try {
      loadThingMapping(tmpSrc.getLines())
    } finally {
      tmpSrc.close()
    }

    println("Creating thing ID to cast/crew mapping")
    val personThingSrc = Source.fromURI(personThingMap)
    val castCrewByThingId = try {
      loadPersonMapping(personThingSrc.getLines())
    } finally {
      personThingSrc.close()
    }

    try {
      src
        .getLines()
        .zipWithIndex
        .drop(offset)
        .safeTake(limit)
        .foreach {
          case (line, idx) => {
            SqlDumpSanitizer
              .extractPersonFromLine(line, Some(idx))
              .foreach(person => {
                val esPerson =
                  makeEsPerson(person, castCrewByThingId, thingIdToDetails)

                fileRotator.writeLines(
                  Seq(
                    Map(
                      "index" -> Map(
                        "_id" -> person.id.toString,
                        "_index" -> "people"
                      )
                    ).asJson.noSpaces,
                    esPerson.asJson.noSpaces
                  )
                )
              })
          }
        }
    } finally {
      src.close()
    }

    fileRotator.finish()
  }

  implicit private class RichPerson(person: Person) {
    import com.teletracker.common.model.tmdb.{Person => TmdbPerson}

    def tmdbPerson: Option[TmdbPerson] =
      person.metadata.map(_.as[TmdbPerson].right.get)
  }

  private def makeEsPerson(
    person: Person,
    personThingsMap: Map[(UUID, PersonAssociationType), Set[CastCrew]],
    itemById: Map[UUID, (String, ThingType, Int, Slug)]
  ) = {
    val rawPerson = person.tmdbPerson

    val castThingIds = personThingsMap.getOrElse(
      person.id -> PersonAssociationType.Cast,
      Set.empty
    )
    val crewThingIds = personThingsMap.getOrElse(
      person.id -> PersonAssociationType.Crew,
      Set.empty
    )

    val cast = castThingIds.flatMap(member => {
      itemById.get(member.thingId).map {
        case (title, thingType, tmdbId, slug) =>
          val credit = rawPerson
            .flatMap(_.combined_credits)
            .flatMap(_.cast.find(_.id == tmdbId))
          EsPersonCastCredit(
            character = credit.flatMap(_.character).orElse(member.character),
            id = member.thingId,
            `type` = thingType,
            title = credit
              .flatMap(_.original_title)
              .orElse(credit.flatMap(_.title))
              .getOrElse(title),
            slug = slug
          )
      }
    })

    val crew = crewThingIds.flatMap(member => {
      itemById.get(member.thingId).map {
        case (title, thingType, tmdbId, slug) =>
          val credit = rawPerson
            .flatMap(_.combined_credits)
            .flatMap(_.cast.find(_.id == tmdbId))
          EsPersonCrewCredit(
            id = member.thingId,
            `type` = thingType,
            title = credit
              .flatMap(_.original_title)
              .orElse(credit.flatMap(_.title))
              .getOrElse(title),
            department = credit.flatMap(_.department).orElse(member.department),
            job = credit.flatMap(_.job).orElse(member.job),
            slug = slug
          )
      }
    })

    EsPerson(
      adult = rawPerson.flatMap(_.adult),
      biography = rawPerson.flatMap(_.biography),
      birthday = rawPerson
        .flatMap(
          _.birthday
        )
        .flatMap(d => Try(LocalDate.parse(d)).toOption),
      cast_credits = Some(cast.toList),
      crew_credits = Some(
        crew.toList
      ),
      external_ids = Some(
        List(
          person.tmdbId.map(
            id =>
              EsExternalId(
                ExternalSource.TheMovieDb.toString,
                id
              )
          ),
          rawPerson
            .flatMap(_.imdb_id)
            .map(imdb => EsExternalId(ExternalSource.Imdb.toString, imdb))
        ).flatten
      ),
      deathday = rawPerson
        .flatMap(_.deathday)
        .flatMap(d => Try(LocalDate.parse(d)).toOption),
      homepage = rawPerson.flatMap(_.homepage),
      id = person.id,
      images = Some(
        List(
          rawPerson
            .flatMap(_.profile_path)
            .map(
              profile =>
                EsItemImage(
                  ExternalSource.TheMovieDb.ordinal(),
                  ExternalSource.TheMovieDb.toString,
                  profile,
                  EsImageType.Profile
                )
            )
        ).flatten
      ),
      name = Some(person.name),
      place_of_birth = rawPerson.flatMap(_.place_of_birth),
      popularity = person.popularity,
      slug = person.normalizedName,
      known_for = rawPerson
        .flatMap(_.known_for)
        .map(_.flatMap {
          case Inl(movie) =>
            itemById
              .find {
                case (_, (_, typ, tmdbId, _)) =>
                  typ == ThingType.Movie && tmdbId == movie.id
              }
              .map {
                case (id, (title, typ, _, slug)) =>
                  EsDenormalizedItem(
                    id,
                    movie.original_title.orElse(movie.title).getOrElse(title),
                    typ.toString,
                    slug = slug
                  )
              }
          case Inr(Inl(show)) =>
            itemById
              .find {
                case (_, (_, typ, tmdbId, _)) =>
                  typ == ThingType.Show && tmdbId == show.id
              }
              .map {
                case (id, (_, typ, _, slug)) =>
                  EsDenormalizedItem(
                    id,
                    show.original_name
                      .getOrElse(show.name),
                    typ.toString,
                    slug = slug
                  )
              }

          case _ => sys.error("")
        })
    )
  }

  private def loadThingMapping(
    lines: Iterator[String]
  ): Map[UUID, (String, ThingType, Int, Slug)] = {
    lines
      .flatMap(line => {
        try {
          val Array(
            id,
            name,
            normaliedName,
            thingType,
            _,
            _,
            _,
            tmdbId,
            _,
            _
          ) = line.split("\t", 10)

          Some(
            UUID.fromString(id) -> (name, ThingType
              .fromString(thingType), tmdbId.toInt, Slug.raw(normaliedName))
          )
        } catch {
          case NonFatal(e) =>
            println(line)
            None
        }
      })
      .toMap
  }

  private def loadPersonMapping(
    lines: Iterator[String]
  ): Map[(UUID, PersonAssociationType), Set[CastCrew]] = {
    val mm = new mutable.HashMap[(UUID, PersonAssociationType), mutable.Set[
      CastCrew
    ]]() with mutable.MultiMap[(UUID, PersonAssociationType), CastCrew]
    lines.foreach(line => {
      try {
        val Array(
          personId,
          thingId,
          relationType,
          character,
          order,
          department,
          job,
          name
        ) = line.split("\t", 8)

        val characterName =
          if (character.isEmpty || character == "\\N") None else Some(character)
        val orderSanitized =
          if (order.isEmpty || order == "\\N") None else Some(order.toInt)

        mm.addBinding(
          UUID.fromString(personId) -> PersonAssociationType.fromString(
            relationType
          ),
          CastCrew(
            UUID.fromString(thingId),
            PersonAssociationType.fromString(relationType),
            characterName,
            orderSanitized,
            if (name.trim.isEmpty || name == "\\N") None else Some(name.trim),
            if (department.trim.isEmpty || department == "\\N") None
            else Some(department.trim),
            if (job.trim.isEmpty || job == "\\N") None else Some(job.trim)
          )
        )
      } catch {
        case NonFatal(e) =>
          println(line)
      }
    })

    mm.toMap.mapValues(_.toSet)
  }

  case class CastCrew(
    thingId: UUID,
    relationType: PersonAssociationType,
    character: Option[String],
    order: Option[Int],
    name: Option[String],
    department: Option[String],
    job: Option[String])
}
