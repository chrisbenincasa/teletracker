package com.teletracker.common.process.tmdb

import com.google.common.cache.Cache
import com.teletracker.common.cache.JustWatchLocalCache
import com.teletracker.common.db.access.{AsyncThingsDbAccess, NetworksDbAccess}
import com.teletracker.common.db.model._
import com.teletracker.common.external.justwatch.JustWatchClient
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.inject.RecentlyProcessedCollections
import com.teletracker.common.model.justwatch.{
  PopularItem,
  PopularItemsResponse,
  PopularSearchRequest
}
import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessResult,
  ProcessSuccess
}
import com.teletracker.common.process.tmdb.TmdbProcessMessage.ProcessBelongsToCollections
import com.teletracker.common.util.{GenreCache, NetworkCache}
import javax.inject.Inject
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

class TmdbMovieImporter @Inject()(
  thingsDbAccess: AsyncThingsDbAccess,
  networksDbAccess: NetworksDbAccess,
  tmdbClient: TmdbClient,
  justWatchClient: JustWatchClient,
  availabilities: Availabilities,
  processQueue: ProcessQueue[TmdbProcessMessage],
  @RecentlyProcessedCollections recentlyProcessedCollections: Cache[
    Integer,
    java.lang.Boolean
  ],
  justWatchLocalCache: JustWatchLocalCache,
  networkCache: NetworkCache,
  tmdbSynchronousProcessor: TmdbSynchronousProcessor,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext)
    extends TmdbImporter(thingsDbAccess) {

  def handleMovies(movies: List[Movie]): Future[List[ProcessResult]] = {
    Future.sequence(movies.map(handleMovie))
  }

  def saveMovie(movie: Movie) = {
    val genreIds =
      movie.genre_ids.orElse(movie.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = genreCache.get()

    val t = ThingFactory.makeThing(movie)

    (for {
      thing <- Promise
        .fromTry(t)
        .future
      genres <- genresFut
    } yield {
      val matchedGenres = genreIds.toList
        .flatMap(id => {
          genres.get(ExternalSource.TheMovieDb -> id.toString)
        })
        .flatMap(_.id)

      thingsDbAccess.saveThingRaw(
        thing.copy(genres = Some(matchedGenres)),
        Some(ExternalSource.TheMovieDb -> movie.id.toString)
      )
    }).flatMap(identity)
  }

  def handleMovie(movie: Movie): Future[ProcessResult] = {
    val saveThingFut = saveMovie(movie)

    val availability = handleMovieAvailability(movie, saveThingFut)

    val saveCollectionFut =
      saveThingFut.flatMap(
        thing => {
          movie.belongs_to_collection
            .map(collection => {
              recentlyProcessedCollections.synchronized {
                if (Option(
                      recentlyProcessedCollections.getIfPresent(collection.id)
                    ).isEmpty) {
                  processQueue.enqueue(
                    TmdbProcessMessage
                      .make(
                        ProcessBelongsToCollections(thing.id, collection)
                      )
                  )
                }
              }

              thingsDbAccess
                .findCollectionByTmdbId(collection.id.toString)
                .flatMap {
                  case None => Future.unit
                  case Some(coll) =>
                    thingsDbAccess
                      .addThingToCollection(coll.id, thing.id)
                      .map(_ => {})
                }
            })
            .getOrElse(Future.unit)
        }
      )

    val saveExternalIds = saveThingFut.map(thing => {
      handleExternalIds(
        Left(thing),
        movie.external_ids,
        Some(movie.id.toString)
      ).map(_ => thing)
    })

    val saveGenres = for {
      savedThing <- saveThingFut
      genres <- thingsDbAccess.findTmdbGenres(
        movie.genre_ids
          .orElse(movie.genres.map(_.map(_.id)))
          .getOrElse(Nil)
          .toSet
      )
      _ <- Future.sequence(genres.map(g => {
        val ref = ThingGenre(savedThing.id, g.id.get)
        thingsDbAccess.saveGenreAssociation(ref)
      }))
    } yield {}

    saveThingFut.foreach(thing => {
      movie.belongs_to_collection.foreach(collection => {
        processQueue.enqueue(
          TmdbProcessMessage
            .make(ProcessBelongsToCollections(thing.id, collection))
        )
      })
    })

    val castAssociationFut = saveThingFut.flatMap(thingLike => {
      movie.credits
        .map(tmdbSynchronousProcessor.processMovieCredits(thingLike.id, _))
        .getOrElse(Future.successful(Nil))
    })

    val result = for {
      savedThing <- saveThingFut
      _ <- saveExternalIds
      _ <- saveGenres
      _ <- availability
      _ <- saveCollectionFut
      _ <- castAssociationFut
    } yield ProcessSuccess(movie.id.toString, savedThing)

    result.recover {
      case NonFatal(e) => ProcessFailure(e)
    }
  }

  def handleMovieAvailability(
    movie: Movie,
    processedMovieFut: Future[ThingLike]
  ): Future[ThingLike] = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val query = PopularSearchRequest(1, 10, movie.title.get, List("movie"))
    val justWatchResFut = justWatchLocalCache.getOrSet(query, {
      justWatchClient.makeRequest[PopularItemsResponse](
        "/content/titles/en_US/popular",
        Seq("body" -> query.asJson.noSpaces)
      )
    })

    (for {
      justWatchRes <- justWatchResFut
      networksBySource <- networkCache.get()
      thing <- processedMovieFut
    } yield {
      val matchingMovie = matchJustWatchItem(movie, justWatchRes.items)

      val availabilities = matchingMovie
        .collect {
          case matchedItem if matchedItem.offers.exists(_.nonEmpty) =>
            for {
              offer <- matchedItem.offers.get.distinct
              provider <- networksBySource
                .get(ExternalSource.JustWatch -> offer.provider_id.toString)
                .toList
            } yield {
              val offerType = Try(
                offer.monetization_type.map(OfferType.fromJustWatchType)
              ).toOption.flatten
              val presentationType = Try(
                offer.presentation_type.map(PresentationType.fromJustWatchType)
              ).toOption.flatten

              Availability(
                id = None,
                isAvailable = true,
                region = offer.country,
                numSeasons = None,
                startDate = offer.date_created.map(
                  LocalDate
                    .parse(_, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                ),
                endDate = None,
                offerType = offerType,
                cost = offer.retail_price.map(BigDecimal.decimal),
                currency = offer.currency,
                thingId = Some(thing.id),
                tvShowEpisodeId = None,
                networkId = provider.id,
                presentationType = presentationType
              )
            }
        }
        .getOrElse(Nil)

      thingsDbAccess.saveAvailabilities(availabilities).map(_ => thing)
    }).flatMap(identity)
  }
}

case class TmdbMoveImportRequest(
  movie: Movie,
  handleExtraWorkAsync: Boolean)
