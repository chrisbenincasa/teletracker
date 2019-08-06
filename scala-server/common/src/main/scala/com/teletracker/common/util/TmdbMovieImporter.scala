package com.teletracker.common.util

import com.google.common.cache.Cache
import com.teletracker.common.cache.JustWatchLocalCache
import com.teletracker.common.db.access.{NetworksDbAccess, ThingsDbAccess}
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
import com.teletracker.common.process.tmdb.TmdbProcessMessage
import com.teletracker.common.process.tmdb.TmdbProcessMessage.ProcessBelongsToCollections
import com.twitter.logging.Logger
import javax.inject.Inject
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

class TmdbMovieImporter @Inject()(
  thingsDbAccess: ThingsDbAccess,
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
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends TmdbImporter(thingsDbAccess) {
  private val logger = Logger()

  def handleMovies(movies: List[Movie]): Future[List[ProcessResult]] = {
    Future.sequence(movies.map(handleMovie))
  }

  def handleMovie(movie: Movie): Future[ProcessResult] = {
    val genreIds =
      movie.genre_ids.orElse(movie.genres.map(_.map(_.id))).getOrElse(Nil).toSet
    val genresFut = thingsDbAccess.findTmdbGenres(genreIds)

    val now = OffsetDateTime.now()
    val t = ThingFactory.makeThing(movie)

    val saveThingFut = Promise
      .fromTry(t)
      .future
      .flatMap(thing => {
        thingsDbAccess.saveThing(
          thing,
          Some(ExternalSource.TheMovieDb -> movie.id.toString)
        )
      })

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
      genres <- genresFut
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

    val result = for {
      savedThing <- saveThingFut
      _ <- saveExternalIds
      _ <- saveGenres
      _ <- availability
      _ <- saveCollectionFut
    } yield ProcessSuccess(movie.id.toString, savedThing)

    result.recover {
      case NonFatal(e) => ProcessFailure(e)
    }
  }

  private def handleMovieAvailability(
    movie: Movie,
    processedMovieFut: Future[Thing]
  ): Future[Thing] = {
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
      val matchingMovie = matchJustWatchMovie(movie, justWatchRes.items)

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

  private def matchJustWatchMovie(
    movie: Movie,
    popularItems: List[PopularItem]
  ): Option[PopularItem] = {
    popularItems.find(item => {
      val idMatch = item.scoring
        .getOrElse(Nil)
        .exists(
          s =>
            s.provider_type == "tmdb:id" && s.value.toInt.toString == movie.id.toString
        )
      val nameMatch = item.title.exists(movie.title.contains)
      val originalMatch =
        movie.original_title.exists(item.original_title.contains)
      val yearMatch = item.original_release_year.exists(year => {
        movie.release_date
          .filter(_.nonEmpty)
          .map(LocalDate.parse(_).getYear)
          .contains(year)
      })

      idMatch || (nameMatch && yearMatch) || (originalMatch && yearMatch)
    })
  }
}
