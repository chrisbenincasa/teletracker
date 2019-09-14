package com.teletracker.common.process.tmdb

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.model.tmdb.{
  MediaType,
  MovieId,
  PersonCredit,
  TvShowId
}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbProcessMessage.{
  ProcessMovie,
  ProcessTvShow
}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import shapeless.tag
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class TmdbPersonCreditProcessor @Inject()(
  thingsDbAccess: ThingsDbAccess,
  processQueue: ProcessQueue[TmdbProcessMessage]
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  def processPersonCredits(
    results: List[PersonCredit],
    scheduleAsyncWork: Boolean = true
  ): Future[Map[String, PartialThing]] = { // TODO: Refine ID return type
    val filteredResults = results.filter(_.media_type.isDefined)

    val movieResults =
      filteredResults.filter(_.media_type.contains(MediaType.Movie))

    val tvResults = filteredResults.filter(_.media_type.contains(MediaType.Tv))

    val existingMoviesFut = thingsDbAccess
      .findThingsByExternalIds(
        ExternalSource.TheMovieDb,
        movieResults.map(_.id.toString).toSet,
        Some(ThingType.Movie)
      )
      .map(groupByExternalId)

    val existingShowsFut = thingsDbAccess
      .findThingsByExternalIds(
        ExternalSource.TheMovieDb,
        tvResults.map(_.id.toString).toSet,
        Some(ThingType.Show)
      )
      .map(groupByExternalId)

    // Partition results by things we've already seen and saved
    val partitionedResults = for {
      existingMovies <- existingMoviesFut
      existingShows <- existingShowsFut
    } yield {
      filteredResults
        .partition(result => {
          val id = result.id.toString
          result.media_type.get match {
            case MediaType.Movie => !existingMovies.isDefinedAt(id)
            case MediaType.Tv    => !existingShows.isDefinedAt(id)
          }
        })
    }

    // Kick off background tasks for updating the full metadata for all results
    if (scheduleAsyncWork) {
      partitionedResults.foreach {
        case (missing, existing) =>
          val all = missing ++ existing
          val movies = all.filter(_.media_type.contains(MediaType.Movie))
          val shows = all.filter(_.media_type.contains(MediaType.Tv))
          movies
            .map(m => ProcessMovie(tag[MovieId](m.id)))
            .map(TmdbProcessMessage.make)
            .foreach(processQueue.enqueue)
          shows
            .map(m => ProcessTvShow(tag[TvShowId](m.id)))
            .map(TmdbProcessMessage.make)
            .foreach(processQueue.enqueue)
      }
    }

    (for {
      existingMovies <- existingMoviesFut
      existingShows <- existingShowsFut
    } yield {
      val thingFuts = filteredResults.map(result => {
        val id = result.id.toString
        val existingThing = result.media_type.get match {
          case MediaType.Movie => existingMovies.get(id)
          case MediaType.Tv    => existingShows.get(id)
        }

        val newOrExistingThing = existingThing match {
          case Some(existing) =>
            Future.successful(Some(id -> existing))

          case None =>
            Promise
              .fromTry(ThingFactory.toWatchableThing(result))
              .future
              .flatMap(thing => {
                thingsDbAccess
                  .saveThingRaw(thing, Some(ExternalSource.TheMovieDb -> id))
                  .map(id -> _)
              })
              .map(Some(_))
              .recover {
                case NonFatal(ex) =>
                  logger
                    .error("Encountered exception while processing result", ex)
                  None
              }
        }

        newOrExistingThing.map(_.map {
          case (id, thing) => id -> thing.toPartial
        })
      })

      Future.sequence(thingFuts).map(_.flatten)
    }).flatMap(identity).map(_.toMap)
  }

  private def groupByExternalId[T](
    seq: Seq[(ExternalId, T)]
  ): Map[String, T] = {
    seq.collect { case (eid, m) if eid.tmdbId.isDefined => eid.tmdbId.get -> m }.toMap
  }
}
