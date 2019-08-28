package com.teletracker.common.process.tmdb

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  ExternalId,
  ExternalSource,
  PartialThing,
  ThingFactory
}
import com.teletracker.common.model.tmdb.{
  MediaType,
  Movie,
  MovieCredits,
  MovieId,
  MultiTypeXor,
  Person,
  PersonCredit,
  TvShow,
  TvShowId
}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.TmdbProcessMessage.{
  ProcessMovie,
  ProcessSearchResults,
  ProcessTvShow
}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import shapeless.{tag, Coproduct}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class TmdbSynchronousProcessor @Inject()(
  thingsDbAccess: ThingsDbAccess,
  processQueue: ProcessQueue[TmdbProcessMessage]
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  object extractId extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, String] = at { _.id.toString }
    implicit val atShow: Case.Aux[TvShow, String] = at { _.id.toString }
    implicit val atPerson: Case.Aux[Person, String] = at { _.id.toString }
  }

  def processMovies(results: List[Movie]): Future[List[PartialThing]] = {
    processMixedTypes(results.map(Coproduct[MultiTypeXor](_)))
  }

  def processMovieCredits(movieCredits: MovieCredits) = {
    movieCredits.cast.getOrElse(Nil).map(castMember => {})
  }

  def processPersonCredits(
    results: List[PersonCredit],
    scheduleAsyncWork: Boolean = true
  ): Future[Map[String, PartialThing]] = { // TODO: Refine ID return type
    val resultIds = results.map(_.id.toString).toSet
    val existingFut = thingsDbAccess
      .findThingsByExternalIds(ExternalSource.TheMovieDb, resultIds, None)
      .map(groupByExternalId)

    // Partition results by things we've already seen and saved
    val partitionedResults = for {
      existing <- existingFut
    } yield {
      results.partition(result => {
        val id = result.id.toString
        !existing.isDefinedAt(id)
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
      existingThings <- existingFut
    } yield {
      val thingFuts = results.map(result => {
        val id = result.id.toString
        val newOrExistingThing = existingThings.get(id) match {
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

  def processMixedTypes(
    results: List[MultiTypeXor]
  ): Future[List[PartialThing]] = {
    val noPersonResults = results.flatMap(_.filterNot[Person])
    val resultIds = noPersonResults.map(_.fold(extractId)).toSet
    val existingFut = thingsDbAccess
      .findThingsByExternalIds(ExternalSource.TheMovieDb, resultIds, None)
      .map(groupByExternalId)

    // Partition results by things we've already seen and saved
    val partitionedResults = for {
      existing <- existingFut
    } yield {
      noPersonResults.partition(result => {
        val id = result.fold(extractId)
        !existing.isDefinedAt(id)
      })
    }

    // Kick off background tasks for updating the full metadata for all results
    partitionedResults.foreach {
      case (missing, existing) =>
        processQueue.enqueue(
          TmdbProcessMessage.make(ProcessSearchResults(missing ++ existing))
        )
    }

    (for {
      existingThings <- existingFut
    } yield {
      val thingFuts = noPersonResults.map(result => {
        val id = result.fold(extractId)
        val newOrExistingThing = existingThings.get(id) match {
          case Some(existing) =>
            Future.successful(Some(existing))

          case None =>
            Promise
              .fromTry(ThingFactory.toWatchableThing(result))
              .future
              .flatMap(thing => {
                thingsDbAccess
                  .saveThingRaw(thing, Some(ExternalSource.TheMovieDb -> id))
              })
              .map(Some(_))
              .recover {
                case NonFatal(ex) =>
                  logger
                    .error("Encountered exception while processing result", ex)
                  None
              }
        }

        newOrExistingThing.map(_.map(_.toPartial))
      })

      Future.sequence(thingFuts).map(_.flatten)
    }).flatMap(identity)
  }

  private def groupByExternalId[T](
    seq: Seq[(ExternalId, T)]
  ): Map[String, T] = {
    seq.collect { case (eid, m) if eid.tmdbId.isDefined => eid.tmdbId.get -> m }.toMap
  }
}
