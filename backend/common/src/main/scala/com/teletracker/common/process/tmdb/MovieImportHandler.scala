package com.teletracker.common.process.tmdb

import cats.implicits._
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch._
import com.teletracker.common.elasticsearch.async.EsIngestQueue
import com.teletracker.common.elasticsearch.denorm.ItemCreditsDenormalizationHelper
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.{Movie, MovieCountryRelease}
import com.teletracker.common.process.tmdb.MovieImportHandler.{
  MovieImportHandlerArgs,
  MovieImportResult
}
import com.teletracker.common.pubsub.{EsIngestItemDenormArgs, TaskScheduler}
import com.teletracker.common.tasks.TaskMessageHelper
import com.teletracker.common.tasks.model.{
  DenormalizeItemTaskArgs,
  TeletrackerTaskIdentifier
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Movies._
import com.teletracker.common.util.{GenreCache, Slug}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Success, Try}

object MovieImportHandler {
  case class MovieImportHandlerArgs(
    forceDenorm: Boolean,
    dryRun: Boolean,
    insertsOnly: Boolean = false,
    verbose: Boolean = true,
    async: Boolean = false)

  case class MovieImportResult(
    itemId: UUID,
    itemIsNew: Boolean,
    itemChanged: Boolean,
    inserted: Boolean,
    updated: Boolean,
    castNeedsDenorm: Boolean,
    crewNeedsDenorm: Boolean,
    queued: Boolean,
    jsonResult: Option[String])
}

class MovieImportHandler @Inject()(
  genreCache: GenreCache,
  itemSearch: ItemLookup,
  itemUpdater: ItemUpdater,
  personLookup: PersonLookup,
  creditsDenormalizer: ItemCreditsDenormalizationHelper,
  taskScheduler: TaskScheduler,
  itemUpdateQueue: EsIngestQueue
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

  def insertOrUpdate(
    args: MovieImportHandlerArgs,
    item: Movie
  ): Future[MovieImportResult] = {
    if (item.title.isEmpty) {
      Future.failed(
        new IllegalArgumentException(
          s"Movie (id=${item.id}) did not have a title set."
        )
      )
    } else {
      insertOrUpdateInternal(args, item)
    }
  }

  def updateMovie(
    id: UUID,
    item: Movie,
    args: MovieImportHandlerArgs
  ): Future[MovieImportResult] = {
    itemSearch
      .lookupItem(
        Left(id),
        Some(ItemType.Movie),
        shouldMateralizeCredits = false,
        shouldMaterializeRecommendations = false
      )
      .flatMap {
        case None =>
          Future.failed(
            new IllegalArgumentException(s"Could not find item with id = $id")
          )
        case Some(value) =>
          handleExistingMovie(item, value.rawItem, args)
      }
  }

  def delete(
    args: MovieImportHandlerArgs,
    tmdbId: Int
  ): Future[Unit] = {
    itemSearch
      .lookupItemByExternalId(
        ExternalSource.TheMovieDb,
        tmdbId.toString,
        ItemType.Movie
      )
      .flatMap {
        case Some(value) if !args.dryRun =>
          itemUpdater.delete(value.id).map(_ => {})

        case Some(value) =>
          logger.info(s"Would've deleted id = ${value.id}")
          Future.unit

        case None =>
          logger.warn(
            s"Could not find item with TMDb ID = ${tmdbId} when trying to delete"
          )
          Future.unit
      }
  }

  private def insertOrUpdateInternal(
    args: MovieImportHandlerArgs,
    item: Movie
  ): Future[MovieImportResult] = {
    itemSearch
      .lookupItemByExternalId(
        ExternalSource.TheMovieDb,
        item.id.toString,
        ItemType.Movie
      )
      .flatMap {
        case Some(esItem) if !args.insertsOnly =>
          handleExistingMovie(item, esItem, args)

        case Some(esItem) =>
          logger.info(
            s"Skipping processing of ${esItem.id} because of insert-only mode"
          )
          Future.successful(
            MovieImportResult(
              itemId = esItem.id,
              itemIsNew = false,
              inserted = false,
              updated = false,
              castNeedsDenorm = false,
              crewNeedsDenorm = false,
              itemChanged = false,
              jsonResult = None,
              queued = false
            )
          )

        case None =>
          handleNewMovie(item, args)
      }
      .through {
        case Success(result)
            if !args.dryRun && !args.async && (args.forceDenorm || result.itemChanged) =>
          logger.debug(
            s"Scheduling denormalization task item id = ${result.itemId}"
          )

          taskScheduler.schedule(
            TaskMessageHelper.forTaskArgs(
              TeletrackerTaskIdentifier.DENORMALIZE_ITEM_TASK,
              DenormalizeItemTaskArgs.apply(
                itemId = result.itemId,
                creditsChanged = result.castNeedsDenorm || args.forceDenorm,
                crewChanged = result.crewNeedsDenorm || args.forceDenorm,
                dryRun = args.dryRun
              ),
              tags = None
            ),
            Some(
              s"${TeletrackerTaskIdentifier.DENORMALIZE_ITEM_TASK}_${result.itemId}"
            )
          )
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(e.getMessage)
          throw e
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
    existingMovie: EsItem,
    args: MovieImportHandlerArgs
  ): Future[MovieImportResult] = {
    val castCrewFut = lookupCastAndCrew(item)
    val recsFut = buildRecommendations(item)
    val genresFut = genreCache.getReferenceMap()
    val updateFut = for {
      castAndCrew <- castCrewFut
      recommendations <- recsFut
      genres <- genresFut
    } yield {
      val images = EsItemUpdaters.updateImages(
        ExternalSource.TheMovieDb,
        toEsItem.esItemImages(item),
        existingMovie.images.getOrElse(Nil)
      )

      val existingRating =
        existingMovie.ratingsGrouped.get(ExternalSource.TheMovieDb)
      val newRating = toEsItem.esItemRating(item)

      val newTmdbRating = (existingRating, newRating) match {
        case (Some(e), Some(n)) =>
          Some(e.copy(vote_average = n.vote_average, vote_count = n.vote_count))
        case (None, Some(n)) => Some(n)
        case (Some(e), None) => Some(e)
        case _               => None
      }

      val newRatings = newTmdbRating
        .map(
          rating =>
            existingMovie.ratingsGrouped
              .updated(ExternalSource.TheMovieDb, rating)
        )
        .getOrElse(existingMovie.ratingsGrouped)
        .values
        .toList

      val newExternalSources =
        EsItemUpdaters.updateExternalIds(item, existingMovie)

      val cast = buildCast(item, castAndCrew)
      val castNeedsDenorm =
        creditsDenormalizer.castNeedsDenormalization(cast, existingMovie.cast)

      val crew = buildCrew(item, castAndCrew)
      val crewNeedsDenorm =
        creditsDenormalizer.crewNeedsDenormalization(crew, existingMovie.crew)

      val updatedVideos = EsItemUpdaters.updateVideos(
        ExternalSource.TheMovieDb,
        toEsItem.esItemVideos(item),
        existingMovie.videos.getOrElse(Nil)
      )

      val tmdbGenreIds =
        item.genre_ids.orElse(item.genres.map(_.map(_.id)))

      val newMovieGenres = tmdbGenreIds match {
        case Some(value) =>
          val itemGenres = value
            .flatMap(
              id => genres.get(ExternalSource.TheMovieDb, id.toString)
            )

          itemGenres
            .map(genre => {
              EsGenre(
                id = genre.id,
                name = genre.name
              )
            })
            .some
        case None =>
          existingMovie.genres
      }

      val partialUpdates = existingMovie.copy(
        alternative_titles = item.alternative_titles
          .map(_.titles)
          .nested
          .map(
            altTitle =>
              EsItemAlternativeTitle(
                country_code = altTitle.iso_3166_1,
                title = altTitle.title,
                `type` = altTitle.`type`
              )
          )
          .value
          .orElse(existingMovie.alternative_titles),
        adult = item.adult.orElse(existingMovie.adult),
        cast = cast,
        crew = crew,
        genres = newMovieGenres,
        images = images.some,
        external_ids = Some(newExternalSources),
        original_title =
          item.original_title.orElse(existingMovie.original_title),
        overview = item.overview.orElse(existingMovie.overview),
        popularity = item.popularity.orElse(existingMovie.popularity),
        ratings = Some(newRatings),
        recommendations =
          if (recommendations.nonEmpty) Some(recommendations)
          else existingMovie.recommendations,
        release_date = item.release_date
          .filter(_.nonEmpty)
          .map(LocalDate.parse(_))
          .orElse(existingMovie.release_date),
        release_dates = item.release_dates.map(_.results.map(mrd => {
          val earliest = findEarliestReleaseDate(mrd.release_dates)
          EsItemReleaseDate(
            mrd.iso_3166_1,
            earliest.map(_._1.toLocalDate),
            earliest.flatMap(_._2.certification)
          )
        })),
        runtime = item.runtime.orElse(existingMovie.runtime),
        slug = item.releaseYear.map(Slug(item.title.get, _)),
        title = item.title
          .map(StringListOrString.forString)
          .getOrElse(existingMovie.title),
        videos = Some(updatedVideos).filter(_.nonEmpty)
      )

      val itemChanged = partialUpdates != existingMovie
      val updatedItem = if (itemChanged) {
        partialUpdates.copy(
          last_updated = Some(OffsetDateTime.now().toInstant.toEpochMilli)
        )
      } else {
        partialUpdates
      }

      if (args.dryRun) {
        Future.successful {
          val changeJson = if (itemChanged) {
            if (args.verbose) {
              logger.info(
                s"Would've updated id = ${existingMovie.id}:\n${diff(existingMovie.asJson, updatedItem.asJson).asJson.spaces2}"
              )
            }

            Some(itemUpdater.getUpdateJson(updatedItem))
          } else {
            logger.info(s"No-op on item ${existingMovie.id}")

            None
          }

          MovieImportResult(
            itemId = existingMovie.id,
            itemIsNew = false,
            inserted = false,
            updated = false,
            castNeedsDenorm = castNeedsDenorm,
            crewNeedsDenorm = crewNeedsDenorm,
            itemChanged = itemChanged,
            jsonResult = changeJson,
            queued = false
          )
        }
      } else if (itemChanged) {
        if (args.async) {
          itemUpdateQueue
            .queueItemUpdate(
              id = updatedItem.id,
              itemType = updatedItem.`type`,
              doc = updatedItem.asJson,
              denorm = Some(
                EsIngestItemDenormArgs(
                  needsDenorm = true,
                  cast = castNeedsDenorm,
                  crew = crewNeedsDenorm
                )
              )
            )
            .map(_ => {
              MovieImportResult(
                itemId = existingMovie.id,
                itemIsNew = false,
                inserted = false,
                updated = true,
                castNeedsDenorm = castNeedsDenorm,
                crewNeedsDenorm = crewNeedsDenorm,
                itemChanged = itemChanged,
                jsonResult = Some(itemUpdater.getUpdateJson(updatedItem)),
                queued = true
              )
            })
        } else {
          itemUpdater
            .update(
              updatedItem,
              denormArgs = Some(
                EsIngestItemDenormArgs(
                  needsDenorm = true,
                  cast = castNeedsDenorm,
                  crew = crewNeedsDenorm,
                  externalIds = None
                )
              )
            )
            .map(_ => {
              MovieImportResult(
                itemId = existingMovie.id,
                itemIsNew = false,
                inserted = false,
                updated = true,
                castNeedsDenorm = castNeedsDenorm,
                crewNeedsDenorm = crewNeedsDenorm,
                itemChanged = itemChanged,
                jsonResult = Some(itemUpdater.getUpdateJson(updatedItem)),
                queued = false
              )
            })
        }
      } else {
        logger.debug(s"Skipped no-op on item ${existingMovie.id}")
        Future.successful {
          MovieImportResult(
            itemId = existingMovie.id,
            itemIsNew = false,
            inserted = false,
            updated = false,
            castNeedsDenorm = castNeedsDenorm,
            crewNeedsDenorm = crewNeedsDenorm,
            itemChanged = false,
            jsonResult = None,
            queued = false
          )
        }
      }
    }

    updateFut.flatMap(identity)
  }

  private def handleNewMovie(
    item: Movie,
    args: MovieImportHandlerArgs
  ): Future[MovieImportResult] = {
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

      val cast = buildCast(item, castAndCrew)
      val crew = buildCrew(item, castAndCrew)

      val esItem = EsItem(
        adult = item.adult,
        alternative_titles = item.alternative_titles
          .map(_.titles)
          .nested
          .map(
            altTitle =>
              EsItemAlternativeTitle(
                country_code = altTitle.iso_3166_1,
                title = altTitle.title,
                `type` = altTitle.`type`
              )
          )
          .value,
        availability = None,
        cast = cast,
        crew = crew,
        external_ids = Some(toEsItem.esExternalIds(item)),
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
        `type` = ItemType.Movie,
        videos = Option(toEsItem.esItemVideos(item)).filter(_.nonEmpty)
      )

      if (args.dryRun) {
        if (args.verbose) {
          logger.info(
            s"Would've inserted new movie (id = ${item.id}, slug = ${esItem.slug})\n:${esItem.asJson.spaces2}"
          )
        }
        Future.successful {
          MovieImportResult(
            itemId = esItem.id,
            itemIsNew = true,
            inserted = false,
            updated = false,
            castNeedsDenorm = true,
            crewNeedsDenorm = true,
            itemChanged = true,
            jsonResult = Some(itemUpdater.getInsertJson(esItem)),
            queued = false
          )
        }
      } else {
        if (args.async) {
          itemUpdateQueue
            .queueItemInsert(esItem)
            .map(_ => {
              MovieImportResult(
                itemId = esItem.id,
                itemIsNew = true,
                inserted = true,
                updated = false,
                castNeedsDenorm = true,
                crewNeedsDenorm = true,
                itemChanged = true,
                jsonResult = Some(itemUpdater.getInsertJson(esItem)),
                queued = true
              )
            })
        } else {
          itemUpdater
            .insert(esItem)
            .map(_ => {
              MovieImportResult(
                itemId = esItem.id,
                itemIsNew = true,
                inserted = true,
                updated = false,
                castNeedsDenorm = true,
                crewNeedsDenorm = true,
                itemChanged = true,
                jsonResult = Some(itemUpdater.getInsertJson(esItem)),
                queued = false
              )
            })
        }
      }
    }

    updateFut.flatMap(identity)
  }

  private def lookupCastAndCrew(item: Movie) = {
    val castIds =
      item.credits.toList.flatMap(_.cast.toList.flatMap(_.map(_.id)))
    val crewIds =
      item.credits.toList.flatMap(_.crew.toList.flatMap(_.map(_.id)))

    val allIds = (castIds ++ crewIds)
    if (allIds.nonEmpty) {
      personLookup
        .lookupPeopleByExternalIds(
          (castIds ++ crewIds)
            .map(_.toString)
            .map(id => EsExternalId(ExternalSource.TheMovieDb, id))
            .toSet
        )
        .map(_.map {
          case (EsExternalId(_, id), person) => id -> person
        })
    } else {
      Future.successful(Map.empty[String, EsPerson])
    }
  }

  private def buildCast(
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
      .map(id => (ExternalSource.TheMovieDb, id.toString, ItemType.Movie))

    itemSearch
      .lookupItemsByExternalIds(allIds)
      .map(_.map {
        case ((EsExternalId(_, id), _), item) => id -> item
      })
      .map(tmdbIdToItem => {
        item.recommendations.toList
          .flatMap(_.results)
          .flatMap(movie => {
            for {
              title <- movie.title
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
