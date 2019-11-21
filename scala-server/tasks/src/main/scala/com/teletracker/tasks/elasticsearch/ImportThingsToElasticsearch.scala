package com.teletracker.tasks.elasticsearch

import com.teletracker.common.db.model.{
  ExternalSource,
  ObjectMetadata,
  PersonAssociationType,
  Thing,
  ThingType
}
import com.teletracker.common.elasticsearch
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  EsExternalId,
  EsGenre,
  EsImageType,
  EsItem,
  EsItemCastMember,
  EsItemCrewMember,
  EsItemImage,
  EsItemRating,
  EsItemRecommendation,
  EsItemReleaseDate,
  StringListOrString
}
import com.teletracker.common.model.tmdb.MovieCountryRelease
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import java.net.URI
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.{GenreCache, Slug}
import scala.io.Source
import io.circe._
import io.circe.parser._
import io.circe.shapes._
import io.circe.generic.auto._
import io.circe.syntax._
import javax.inject.Inject
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import com.teletracker.common.util.Futures._
import com.twitter.util.StorageUnit
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.NonFatal

/*
 These queries are used to generate the mappings needed for the export:
 \copy (select * from things) to '/Users/christianbenincasa/Desktop/things_dump_full.tsv';
 \copy (select * from availability) to '/Users/christianbenincasa/Desktop/availability_dump.tsv';
 \copy (select pt.*, p.name from person_things pt join people p on pt.person_id = p.id) to '/Users/christianbenincasa/Desktop/person_things.tsv;
 */

class ImportThingsToElasticsearch @Inject()(
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

  lazy val genres = genreCache.get().await()

  override def runInternal(args: Args): Unit = {
    val thingInput = args.value[URI]("thingMapping").get
    val availabilityInput = args.value[URI]("availabilityMapping").get
    val personThingMap = args.value[URI]("personThingMapping").get
    val personIdToSlugInput = args.value[URI]("personSlugMapping").get
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val thingIdFilter = args.value[UUID]("thingIdFilter")
    val outputPath =
      args.valueOrDefault("outputPath", System.getProperty("user.dir"))

    val fileRotator =
      FileRotator.everyNBytes(
        "item_output",
        StorageUnit.fromMegabytes(90),
        Some(outputPath)
      )

    val src = Source.fromURI(thingInput)

    println("Creating tmdb ID to thing ID mapping")
    val tmpSrc = Source.fromURI(thingInput)
    val tmdbIdToThingId = try {
      loadTmdbIdToThingIdMapping(tmpSrc.getLines())
    } finally {
      tmpSrc.close()
    }

    println("Creating thing ID to availability mapping")
    val availabilitySrc = Source.fromURI(availabilityInput)
    val availabilityByThingId = try {
      loadAvailabilityData(availabilitySrc.getLines())
    } finally {
      availabilitySrc.close()
    }

    println("Creating thing ID to cast/crew mapping")
    val personThingSrc = Source.fromURI(personThingMap)
    val castCrewByThingId = try {
      loadPersonMapping(personThingSrc.getLines())
    } finally {
      personThingSrc.close()
    }

    println("Creating person ID to slug mapping")
    val personSlugSrc = Source.fromURI(personIdToSlugInput)
    val personIdToSlug = try {
      loadPersonToSlugMapping(personSlugSrc.getLines())
    } finally {
      personSlugSrc.close()
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
              .extractThingFromLine(line, Some(idx))
              .filter(
                mapped =>
                  thingIdFilter.isEmpty || mapped.id == thingIdFilter.get
              )
              .foreach(thing => {
                val esThing = if (thing.`type` == ThingType.Movie) {
                  handleMovie(
                    thing,
                    tmdbIdToThingId,
                    availabilityByThingId,
                    castCrewByThingId,
                    personIdToSlug
                  )
                } else if (thing.`type` == ThingType.Show) {
                  handleTvShow(
                    thing,
                    tmdbIdToThingId,
                    availabilityByThingId,
                    castCrewByThingId,
                    personIdToSlug
                  )
                } else {
                  throw new IllegalArgumentException
                }

                fileRotator.writeLines(
                  Seq(
                    Map(
                      "index" -> Map(
                        "_id" -> thing.id.toString,
                        "_index" -> "items"
                      )
                    ).asJson.noSpaces,
                    esThing.asJson.noSpaces,
                    System.lineSeparator()
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

  private def loadTmdbIdToThingIdMapping(
    lines: Iterator[String]
  ): Map[String, (UUID, Slug)] = {
    lines
      .flatMap(line => {
        val Array(
          id,
          _,
          normalizedName,
          _,
          _,
          _,
          _,
          tmdbId,
          _,
          _
        ) = line.split("\t", 10)

        if (tmdbId.nonEmpty && tmdbId != "\\N") {
          Some(tmdbId -> (UUID.fromString(id), Slug.raw(normalizedName)))
        } else {
          None
        }
      })
      .toMap
  }

  private def loadAvailabilityData(lines: Iterator[String]) = {
    val mm = new mutable.HashMap[
      UUID,
      scala.collection.mutable.Set[EsAvailability]
    ]() with mutable.MultiMap[UUID, EsAvailability]

    lines.foreach(line => {
      val Array(
        _,
        _,
        region,
        _,
        startDate,
        endDate,
        offerType,
        cost,
        currency,
        thingId,
        _,
        networkId,
        presentationType
      ) = line.split("\t", 14)

      val startLocalDate = Option(startDate)
        .filter(_.nonEmpty)
        .filterNot(_ == "\\N")
        .map(
          OffsetDateTime
            .parse(_, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssX"))
        )
        .map(_.toLocalDate)
      val endLocalDate = Option(endDate)
        .filter(_.nonEmpty)
        .filterNot(_ == "\\N")
        .map(
          OffsetDateTime
            .parse(_, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssX"))
        )
        .map(_.toLocalDate)
      val sanitizedCost =
        if (cost.isEmpty || cost == "\\N") None else Some(cost.toDouble)
      val sanitizedCurrency =
        if (currency.isEmpty || currency == "\\N") None else Some(currency)

      mm.addBinding(
        UUID.fromString(thingId),
        EsAvailability(
          networkId.toInt,
          region,
          startLocalDate,
          endLocalDate,
          offerType,
          sanitizedCost,
          sanitizedCurrency,
          Some(List(presentationType))
        )
      )
    })

    mm.toMap.mapValues(avs => {
      avs
        .groupBy(av => (av.network_id, av.region, av.offer_type))
        .flatMap {
          case (key, values) =>
            combinePresentationTypes(values.toSeq).map(key -> _)
        }
        .values
    })
  }

  private def combinePresentationTypes(
    availabilities: Seq[EsAvailability]
  ): Option[EsAvailability] = {
    if (availabilities.isEmpty) {
      None
    } else {
      Some(availabilities.reduce((l, r) => {
        val combined = l.presentation_types
          .getOrElse(Nil) ++ r.presentation_types.getOrElse(Nil)
        l.copy(presentation_types = Some(combined.distinct))
      }))
    }
  }

  private def loadPersonToSlugMapping(lines: Iterator[String]) = {
    lines
      .map(line => {
        val Array(
          id,
          _,
          normalizedName,
          _,
          _,
          _,
          _,
          _
        ) = line.split("\t", 8)

        UUID.fromString(id) -> Slug.raw(normalizedName)
      })
      .toMap
  }

  private def loadPersonMapping(lines: Iterator[String]) = {
    val mm = new mutable.HashMap[UUID, mutable.Set[CastCrew]]()
    with mutable.MultiMap[UUID, CastCrew]
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
          UUID.fromString(thingId),
          CastCrew(
            UUID.fromString(personId),
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
    personId: UUID,
    relationType: PersonAssociationType,
    character: Option[String],
    order: Option[Int],
    name: Option[String],
    department: Option[String],
    job: Option[String])

  private def createBaseEsItem(
    thing: Thing,
    tmdbIdToThingId: Map[String, (UUID, Slug)],
    availabilityByThingId: Map[UUID, Iterable[EsAvailability]],
    castCrewByThingId: Map[UUID, Set[CastCrew]],
    personIdToSlug: Map[UUID, Slug]
  ) = {
    val cast = castCrewByThingId
      .get(thing.id)
      .map(
        _.filter(_.relationType == PersonAssociationType.Cast)
          .map(cast => {
            EsItemCastMember(
              character = cast.character,
              id = cast.personId,
              order = cast.order.getOrElse(0),
              name = cast.name.getOrElse(""),
              slug = Some(personIdToSlug(cast.personId))
            )
          })
          .toList
      )

    val crew = castCrewByThingId
      .get(thing.id)
      .map(
        _.filter(_.relationType == PersonAssociationType.Crew)
          .map(cast => {
            EsItemCrewMember(
              id = cast.personId,
              order = cast.order,
              name = cast.name.getOrElse(""),
              department = cast.department,
              job = cast.job,
              slug = Some(personIdToSlug(cast.personId))
            )
          })
          .toList
      )

    EsItem(
      adult = None,
      availability = availabilityByThingId.get(thing.id).map(_.toList),
      cast = cast,
      crew = crew,
      external_ids = Some(
        List(
          thing.tmdbId
            .map(id => EsExternalId(ExternalSource.TheMovieDb.toString, id))
        ).flatten
      ),
      genres = thing.genres.map(
        _.flatMap(id => {
          genres.values.find(_.id.contains(id))
        }).map(genre => {
            EsGenre(
              id = genre.id.get,
              name = genre.name
              // TODO include short name?
            )
          })
          .toList
      ),
      id = thing.id,
      images = None,
      original_title = None,
      overview = None,
      popularity = thing.popularity,
      ratings = None,
      recommendations = None,
      release_date = None,
      release_dates = None,
      runtime = None,
      slug = Some(thing.normalizedName),
      tags = None,
      title = StringListOrString.forString(thing.name),
      `type` = thing.`type`
    )
  }

  private def handleMovie(
    thing: Thing,
    tmdbIdToThingId: Map[String, (UUID, Slug)],
    availabilityByThingId: Map[UUID, Iterable[EsAvailability]],
    castCrewByThingId: Map[UUID, Set[CastCrew]],
    personIdToSlug: Map[UUID, Slug]
  ): EsItem = {
    val rawMovie = thing.metadata.flatMap(_.tmdbMovie).get

    val images = List(
      rawMovie.backdrop_path.map(backdrop => {
        EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = backdrop,
          image_type = EsImageType.Backdrop
        )
      }),
      rawMovie.poster_path.map(poster => {
        EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = poster,
          image_type = EsImageType.Poster
        )
      })
    ).flatten

    val ratings = List(rawMovie.vote_average.map(voteAverage => {
      EsItemRating(
        provider_id = ExternalSource.TheMovieDb.ordinal(),
        provider_shortname = ExternalSource.TheMovieDb.getName,
        vote_average = voteAverage,
        vote_count = rawMovie.vote_count
      )
    })).flatten

    val recommendations = rawMovie.recommendations.toList
      .flatMap(_.results)
      .flatMap(movie => {
        for {
          title <- movie.title.orElse(movie.original_title)
          (id, slug) <- tmdbIdToThingId.get(movie.id.toString)
        } yield {
          EsItemRecommendation(
            id = id,
            title = title,
            slug = slug
          )
        }
      })

    val externalIds = rawMovie.external_ids
      .flatMap(
        ids =>
          ids.imdb_id
            .filter(_.nonEmpty)
            .map(
              imdb => EsExternalId(ExternalSource.Imdb.toString, imdb)
            )
      )
      .toList

    val releaseDates = rawMovie.release_dates.map(_.results.map(mrd => {
      val earliest = findEarliestReleaseDate(mrd.release_dates)
      EsItemReleaseDate(
        mrd.iso_3166_1,
        earliest.map(_._1.toLocalDate),
        earliest.flatMap(_._2.certification)
      )
    }))

    val baseItem = createBaseEsItem(
      thing,
      tmdbIdToThingId,
      availabilityByThingId,
      castCrewByThingId,
      personIdToSlug
    )

    baseItem.copy(
      adult = rawMovie.adult,
      external_ids = Some(baseItem.external_ids.getOrElse(Nil) ++ externalIds),
      images = if (images.isEmpty) None else Some(images),
      original_title = rawMovie.original_title,
      overview = rawMovie.overview,
      ratings = if (ratings.isEmpty) None else Some(ratings),
      recommendations =
        if (recommendations.isEmpty) None else Some(recommendations),
      release_date = rawMovie.release_date.map(LocalDate.parse),
      release_dates = releaseDates,
      runtime = rawMovie.runtime
    )
  }

  private def handleTvShow(
    thing: Thing,
    tmdbIdToThingId: Map[String, (UUID, Slug)],
    availabilityByThingId: Map[UUID, Iterable[EsAvailability]],
    castCrewByThingId: Map[UUID, Set[CastCrew]],
    personIdToSlug: Map[UUID, Slug]
  ): EsItem = {
    val rawShow = thing.metadata.flatMap(_.tmdbShow).get

    val images = List(
      rawShow.backdrop_path.map(backdrop => {
        EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = backdrop,
          image_type = EsImageType.Backdrop
        )
      }),
      rawShow.poster_path.map(poster => {
        EsItemImage(
          provider_id = ExternalSource.TheMovieDb.ordinal(), // TMDb, for now
          provider_shortname = ExternalSource.TheMovieDb.getName,
          id = poster,
          image_type = EsImageType.Poster
        )
      })
    ).flatten

    val recommendations = rawShow.recommendations.toList
      .flatMap(_.results)
      .flatMap(movie => {
        for {
          (id, slug) <- tmdbIdToThingId.get(movie.id.toString)
        } yield {
          EsItemRecommendation(
            id = id,
            title = movie.name,
            slug = slug
          )
        }
      })

    val ratings = List(rawShow.vote_average.map(voteAverage => {
      EsItemRating(
        provider_id = ExternalSource.TheMovieDb.ordinal(),
        provider_shortname = ExternalSource.TheMovieDb.getName,
        vote_average = voteAverage,
        vote_count = rawShow.vote_count
      )
    })).flatten

    val externalIds = rawShow.external_ids
      .flatMap(
        ids =>
          ids.imdb_id
            .filter(_.nonEmpty)
            .map(
              imdb => EsExternalId(ExternalSource.Imdb.toString, imdb)
            )
      )
      .toList

    val releaseDates =
      rawShow.content_ratings
        .map(_.results.map(rating => {
          EsItemReleaseDate(
            rating.iso_3166_1,
            None,
            Some(rating.rating)
          )
        }))
        .getOrElse(Nil)

    val baseItem = createBaseEsItem(
      thing,
      tmdbIdToThingId,
      availabilityByThingId,
      castCrewByThingId,
      personIdToSlug
    )

    baseItem.copy(
      adult = None,
      external_ids = Some(baseItem.external_ids.getOrElse(Nil) ++ externalIds),
      images = if (images.isEmpty) None else Some(images),
      original_title = rawShow.original_name,
      overview = rawShow.overview,
      ratings = if (ratings.isEmpty) None else Some(ratings),
      recommendations =
        if (recommendations.isEmpty) None else Some(recommendations),
      release_date = rawShow.first_air_date.map(LocalDate.parse),
      release_dates = Some(releaseDates),
      runtime = rawShow.episode_run_time.flatMap(_.headOption)
    )
  }

  private def findEarliestReleaseDate(releases: List[MovieCountryRelease]) = {
    releases
      .flatMap(release => {
        release.release_date
          .flatMap(rd => Try(OffsetDateTime.parse(rd)).toOption)
          .map(dt => dt -> release)
      })
      .sortWith {
        case ((dt1, _), (dt2, _)) => dt1.isBefore(dt2)
      }
      .headOption
  }
}
