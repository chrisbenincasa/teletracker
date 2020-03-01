package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.model.{ExternalSource, ThingType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{Movie, MovieCountryRelease}
import com.teletracker.common.util.Movies._
import com.teletracker.common.util.{GenreCache, Slug}
import com.teletracker.tasks.tmdb.import_tasks.MovieImportHandler.MovieImportHandlerArgs
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

object MovieImportHandler {
  case class MovieImportHandlerArgs(dryRun: Boolean)
}

class MovieImportHandler @Inject()(
  genreCache: GenreCache,
  itemSearch: ItemLookup,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup
)(implicit executor: ExecutionContext) {
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private def toEsItem: ToEsItem[Movie] =
    ToEsItem.forTmdbMovie

  def handleItem(
    args: MovieImportHandlerArgs,
    item: Movie
  ): Future[Unit] = {
    itemSearch
      .lookupItemByExternalId(
        ExternalSource.TheMovieDb,
        item.id.toString,
        ThingType.Movie
      )
      .flatMap {
        case Some(esItem) =>
          handleExistingMovie(item, esItem, args)

        case None =>
          handleNewMovie(item, args)
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(e.getMessage)
      }
  }

  private def findEarliestReleaseDate(releases: List[MovieCountryRelease]) = {
    releases
      .flatMap(release => {
        release.release_date
          .filter(_.nonEmpty)
          .flatMap(rd => Try(OffsetDateTime.parse(rd)).toOption)
          .map(dt => dt -> release)
      })
      .sortWith {
        case ((dt1, _), (dt2, _)) => dt1.isBefore(dt2)
      }
      .headOption
  }

  private def handleExistingMovie(
    item: Movie,
    value: EsItem,
    args: MovieImportHandlerArgs
  ) = {
    val castCrewFut = lookupCastAndCrew(item)
    val recsFut = buildRecommendations(item)
    val updateFut = for {
      castAndCrew <- castCrewFut
      recommendations <- recsFut
    } yield {
      val images =
        toEsItem
          .esItemImages(item)
          .foldLeft(value.imagesGrouped)((acc, image) => {
            val externalSource =
              ExternalSource.fromString(image.provider_shortname)
            acc.get(externalSource -> image.image_type) match {
              case Some(_) =>
                acc.updated((externalSource, image.image_type), image)
              case None => acc
            }
          })
          .values

      val newRatings = toEsItem
        .esItemRating(item)
        .map(
          rating =>
            value.ratingsGrouped.updated(ExternalSource.TheMovieDb, rating)
        )
        .getOrElse(value.ratingsGrouped)
        .values
        .toList

      val extractedExternalIds = List(
        toEsItem.esExternalId(item),
        item.external_ids
          .flatMap(_.imdb_id)
          .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
      ).flatten

      val newExternalSources = extractedExternalIds
        .foldLeft(value.externalIdsGrouped)(
          (acc, id) =>
            acc.updated(ExternalSource.fromString(id.provider), id.id)
        )
        .toList
        .map(Function.tupled(EsExternalId.apply))

      val cast = buildCredits(item, castAndCrew)
      val crew = buildCrew(item, castAndCrew)

      val partialUpdates = value.copy(
        adult = value.adult,
        cast = cast,
        crew = crew,
        images = Some(images.toList),
        last_updated = Some(OffsetDateTime.now().toInstant.toEpochMilli),
        external_ids = Some(newExternalSources),
        original_title = item.original_title.orElse(value.original_title),
        overview = item.overview.orElse(value.overview),
        popularity = item.popularity.orElse(value.popularity),
        ratings = Some(newRatings),
        recommendations =
          if (recommendations.nonEmpty) Some(recommendations)
          else value.recommendations,
        release_date = item.release_date
          .filter(_.nonEmpty)
          .map(LocalDate.parse(_))
          .orElse(value.release_date)
      )

      if (args.dryRun) {
        Future.successful {
          logger.info(
            s"Would've updated id = ${value.id}:\n${diff(value.asJson, partialUpdates.asJson).asJson.spaces2}"
          )
        }
      } else {
        itemUpdater.update(partialUpdates).map(_ => {})
      }
    }

    updateFut.flatMap(identity)
  }

  private def handleNewMovie(
    item: Movie,
    args: MovieImportHandlerArgs
  ) = {
    val recsFut = buildRecommendations(item)
    val castCrewFut = lookupCastAndCrew(item)
    val updateFut = for {
      genres <- genreCache.getReferenceMap()
      castAndCrew <- castCrewFut
      recommendations <- recsFut
    } yield {
      val tmdbGenreIds =
        item.genre_ids.orElse(item.genres.map(_.map(_.id)))

      val itemGenres = tmdbGenreIds
        .getOrElse(Nil)
        .flatMap(
          id => genres.get(ExternalSource.TheMovieDb, id.toString)
        )

      val cast = buildCredits(item, castAndCrew)
      val crew = buildCrew(item, castAndCrew)

      val esItem = EsItem(
        adult = None,
        availability = None,
        cast = cast,
        crew = crew,
        external_ids = Some(
          List(
            toEsItem.esExternalId(item),
            item.external_ids
              .flatMap(_.imdb_id)
              .map(imdb => EsExternalId(ExternalSource.Imdb, imdb))
          ).flatten
        ),
        genres = Some(
          itemGenres
            .map(genre => {
              EsGenre(
                id = genre.id,
                name = genre.name
              )
            })
        ),
        id = UUID.randomUUID(),
        images = Some(toEsItem.esItemImages(item)),
        last_updated = Some(OffsetDateTime.now().toInstant.toEpochMilli),
        original_title = item.original_title,
        overview = item.overview,
        popularity = item.popularity,
        ratings = Some(toEsItem.esItemRating(item).toList),
        recommendations = Some(recommendations),
        release_date =
          item.release_date.filter(_.nonEmpty).map(LocalDate.parse(_)),
        release_dates = item.release_dates.map(_.results.map(mrd => {
          val earliest = findEarliestReleaseDate(mrd.release_dates)
          EsItemReleaseDate(
            mrd.iso_3166_1,
            earliest.map(_._1.toLocalDate),
            earliest.flatMap(_._2.certification)
          )
        })),
        runtime = item.runtime,
        slug = item.releaseYear.map(Slug(item.title.get, _)),
        tags = None,
        title = StringListOrString.forString(item.title.get),
        `type` = ThingType.Movie
      )

      if (args.dryRun) {
        Future.successful {
          logger.info(
            s"Would've inserted new movie (id = ${item.id}, slug = ${esItem.slug})\n:${esItem.asJson.spaces2}"
          )
        }
      } else {
        itemUpdater.insert(esItem)
      }
    }

    updateFut.flatMap(identity).map(_ => {})
  }

  private def lookupCastAndCrew(item: Movie) = {
    val castIds =
      item.credits.toList.flatMap(_.cast.toList.flatMap(_.map(_.id)))
    val crewIds =
      item.credits.toList.flatMap(_.crew.toList.flatMap(_.map(_.id)))

    val allIds = (castIds ++ crewIds)
    if (allIds.nonEmpty) {
      personLookup.lookupPeopleByExternalIds(
        ExternalSource.TheMovieDb,
        (castIds ++ crewIds).distinct.map(_.toString)
      )
    } else {
      Future.successful(Map.empty[String, EsPerson])
    }
  }

  private def buildCredits(
    item: Movie,
    matchedCastAndCrew: Map[String, EsPerson]
  ) = {
    item.credits
      .flatMap(_.cast)
      .map(_.flatMap(castMember => {
        matchedCastAndCrew
          .get(castMember.id.toString)
          .map(person => {
            EsItemCastMember(
              character = castMember.character.filter(_.nonEmpty),
              id = person.id,
              order = castMember.order.getOrElse(0),
              name = person.name.getOrElse(""),
              slug = person.slug
            )
          })
      }).sortWith(EsOrdering.forItemCastMember))
  }

  private def buildCrew(
    item: Movie,
    matchedCastAndCrew: Map[String, EsPerson]
  ) = {
    item.credits
      .flatMap(_.crew)
      .map(_.flatMap(crewMember => {
        matchedCastAndCrew
          .get(crewMember.id.toString)
          .map(person => {
            EsItemCrewMember(
              id = person.id,
              name = person.name.getOrElse(""),
              slug = person.slug,
              order = None,
              department = crewMember.department.filter(_.nonEmpty),
              job = crewMember.job.filter(_.nonEmpty)
            )
          })
      }).sortWith(EsOrdering.forItemCrewMember))
  }

  private def buildRecommendations(item: Movie) = {
    val allIds = item.recommendations.toList
      .flatMap(_.results)
      .map(_.id)
      .map(id => (ExternalSource.TheMovieDb, id.toString, ThingType.Movie))

    itemSearch
      .lookupItemsByExternalIds(allIds)
      .map(_.map {
        case ((_, id), item) => id -> item
      })
      .map(tmdbIdToItem => {
        item.recommendations.toList
          .flatMap(_.results)
          .flatMap(movie => {
            for {
              title <- movie.title.orElse(movie.original_title)
              foundItem <- tmdbIdToItem.get(movie.id.toString)
            } yield {
              EsItemRecommendation(
                id = foundItem.id,
                title = title,
                slug = foundItem.slug
              )
            }
          })
      })
  }
}
