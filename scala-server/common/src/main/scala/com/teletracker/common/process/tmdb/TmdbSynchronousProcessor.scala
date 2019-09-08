package com.teletracker.common.process.tmdb

import com.google.inject.Provider
import com.teletracker.common.db.access.AsyncThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import com.teletracker.common.process.ProcessQueue
import com.teletracker.common.process.tmdb.FieldExtractors.{
  extractId,
  extractType
}
import com.teletracker.common.process.tmdb.TmdbProcessMessage.{
  ProcessMovie,
  ProcessTvShow
}
import com.teletracker.common.util.GenreCache
import javax.inject.Inject
import org.slf4j.LoggerFactory
import shapeless.{tag, Coproduct, Poly1}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class TmdbSynchronousProcessor @Inject()(
  thingsDbAccess: AsyncThingsDbAccess,
  processQueue: ProcessQueue[TmdbProcessMessage],
  genreCache: GenreCache,
  tmdbMovieImporter: Provider[TmdbMovieImporter],
  tmdbShowImporter: TmdbShowImporter,
  tmdbPersonImporter: TmdbPersonImporter
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  def processMovies(results: List[Movie]): Future[List[PartialThing]] = {
    processMixedTypes(results.map(Coproduct[MultiTypeXor](_)))
  }

  def processMovieCredits(
    thingId: UUID,
    movieCredits: MovieCredits
  ): Future[List[PersonThing]] = {
    val cast = movieCredits.cast.getOrElse(Nil)
    val crew = movieCredits.crew.getOrElse(Nil)
    val ids = (cast.map(_.id.toString).toSet ++ crew.map(_.id.toString).toSet)
    thingsDbAccess
      .findPeopleByTmdbIds(ids)
      .flatMap(peopleById => {
        val castIds = cast.map(_.id.toString).toSet
        val crewIds = crew.map(_.id.toString).toSet
        // TODO scrape
        val missingCast = castIds -- peopleById.keySet
        val missingCrew = crewIds -- peopleById.keySet

        val castAssociations = cast.flatMap(castMember => {
          peopleById
            .get(castMember.id.toString)
            .map(person => {
              PersonThing(
                person.id,
                thingId,
                PersonAssociationType.Cast,
                castMember.character_name.orElse(castMember.character),
                castMember.order
              )
            })
        })

        val crewAssociations = crew.flatMap(castMember => {
          peopleById
            .get(castMember.id.toString)
            .map(person => {
              PersonThing(
                person.id,
                thingId,
                PersonAssociationType.Crew,
                None,
                None
              )
            })
        })

        val saves = (castAssociations ++ crewAssociations)
          .map(thingsDbAccess.upsertPersonThing)

        Future.sequence(saves)
      })
  }

  def processMixedTypes(
    results: List[MultiTypeXor]
  ): Future[List[PartialThing]] = {
    val noPersonResults = results.flatMap(_.filterNot[TmdbPerson])
    val resultIds = noPersonResults.map(_.fold(FieldExtractors.extractId)).toSet
    val existingFut = thingsDbAccess
      .findThingsByTmdbIds(ExternalSource.TheMovieDb, resultIds, None)

    // Partition results by things we've already seen and saved
    val partitionedResults = for {
      existing <- existingFut
    } yield {
      noPersonResults.partition(result => {
        val id = result.fold(FieldExtractors.extractId)
        val typ = result.fold(FieldExtractors.extractType)
        !existing.isDefinedAt(id -> typ)
      })
    }

    // Kick off background tasks for updating the full metadata for all results
    partitionedResults.foreach {
      case (missing, existing) =>
        val movieMessages = (missing ++ existing)
          .flatMap(_.filter[Movie])
          .flatMap(_.head)
          .map(_.id)
          .map(id => TmdbProcessMessage.make(ProcessMovie(tag[MovieId](id))))
        val showMessages = (missing ++ existing)
          .flatMap(_.filter[TvShow])
          .flatMap(_.head)
          .map(_.id)
          .map(id => TmdbProcessMessage.make(ProcessTvShow(tag[TvShowId](id))))

        (movieMessages ++ showMessages).foreach(processQueue.enqueue)
    }

    (for {
      existingThings <- existingFut
    } yield {
      val thingFuts = noPersonResults.map(result => {
        val id = result.fold(extractId)
        val typ = result.fold(extractType)

        val newOrExistingThing = existingThings.get(id -> typ) match {
          case Some(existing) =>
            Future.successful(Some(existing))

          case None =>
            result
              .fold(saveResult)
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

  object saveResult extends Poly1 {
    implicit val atMovie: Case.Aux[Movie, Future[ThingRaw]] = at[Movie] {
      tmdbMovieImporter.get().saveMovie
    }
    implicit val atShow: Case.Aux[TvShow, Future[ThingRaw]] = at[TvShow] {
      tmdbShowImporter.saveShow
    }
  }

  private def groupByExternalId[T](
    seq: Seq[(ExternalId, T)]
  ): Map[String, T] = {
    seq.collect { case (eid, m) if eid.tmdbId.isDefined => eid.tmdbId.get -> m }.toMap
  }
}
