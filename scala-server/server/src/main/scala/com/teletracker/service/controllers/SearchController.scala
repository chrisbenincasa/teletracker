package com.teletracker.service.controllers

import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.config.TeletrackerConfig
import com.teletracker.service.db.access.{ThingsDbAccess, UserThingDetails}
import com.teletracker.service.db.model.{
  ExternalId,
  ExternalSource,
  PartialThing,
  ThingFactory
}
import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.model.DataResponse
import com.teletracker.service.model.tmdb._
import com.teletracker.service.process.ProcessQueue
import com.teletracker.service.process.tmdb.TmdbProcessMessage.ProcessSearchResults
import com.teletracker.service.process.tmdb.{
  TmdbEntityProcessor,
  TmdbProcessMessage
}
import com.teletracker.service.util.TmdbMovieImporter
import com.teletracker.service.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import shapeless.Coproduct
import scala.concurrent.{ExecutionContext, Future}

class SearchController @Inject()(
  config: TeletrackerConfig,
  thingsDbAccess: ThingsDbAccess,
  tmdbClient: TmdbClient,
  resultProcessor: TmdbEntityProcessor,
  movieImporter: TmdbMovieImporter,
  processQueue: ProcessQueue[TmdbProcessMessage]
)(implicit executionContext: ExecutionContext)
    extends Controller {
  prefix("/api/v1") {
    filter[JwtAuthFilter].apply {
      get("/search") { req: Request =>
        val query = req.params("query")

        tmdbClient
          .makeRequest[SearchResult]("search/multi", Seq("query" -> query))
          .flatMap(handleSearchMultiResult(req.authContext.user.id, _))
          .map(result => {
            response.ok.contentTypeJson().body(DataResponse.complex(result))
          })
      }
    }
  }

  object extractId extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, String] = at { _.id.toString }
    implicit val atShow: Case.Aux[TvShow, String] = at { _.id.toString }
    implicit val atPerson: Case.Aux[Person, String] = at { _.id.toString }
  }

  private def handleSearchMovieResult(
    userId: Int,
    result: MovieSearchResult
  ): Future[List[PartialThing]] = {
    val movies = result.results.map(Coproduct[MultiTypeXor](_))
    handleSearchMultiResult(userId, movies)
  }

  private def handleSearchMultiResult(
    userId: Int,
    result: SearchResult
  ): Future[List[PartialThing]] = {
    handleSearchMultiResult(userId, result.results)
  }

  private def handleSearchMultiResult(
    userId: Int,
    results: List[MultiTypeXor]
  ): Future[List[PartialThing]] = {
    val resultIds = results.map(_.fold(extractId)).toSet
    val existingFut = thingsDbAccess
      .findThingsByExternalIds(ExternalSource.TheMovieDb, resultIds, None)
      .map(groupByExternalId)

    // Find any details on existing things
    val thingDetailsByThingIdFut = existingFut.flatMap(existing => {
      thingsDbAccess
        .getThingsUserDetails(userId, existing.values.flatMap(_.id).toSet)
    })

    // Partition results by things we've already seen and saved
    val partitionedResults = for {
      existing <- existingFut
    } yield {
      results.partition(result => {
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
      thingDetailsByThingId <- thingDetailsByThingIdFut
    } yield {
      val thingFuts = results.map(result => {
        val id = result.fold(extractId)
        val newOrExistingThing = existingThings.get(id) match {
          case Some(existing) =>
            Future.successful(existing)

          case None =>
            val thing = ThingFactory.makeThing(result)
            thingsDbAccess
              .saveThing(thing, Some(ExternalSource.TheMovieDb -> id))
        }

        newOrExistingThing.map(thing => {
          val meta = thing.id
            .flatMap(thingDetailsByThingId.get)
            .getOrElse(UserThingDetails.empty)
          thing.toPartial.withUserMetadata(meta)
        })
      })

      Future.sequence(thingFuts)
    }).flatMap(identity)
  }

  private def groupByExternalId[T](
    seq: Seq[(ExternalId, T)]
  ): Map[String, T] = {
    seq.collect { case (eid, m) if eid.tmdbId.isDefined => eid.tmdbId.get -> m }.toMap
  }
}
