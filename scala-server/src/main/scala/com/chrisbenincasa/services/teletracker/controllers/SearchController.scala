package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.chrisbenincasa.services.teletracker.db.ThingsDbAccess
import com.chrisbenincasa.services.teletracker.db.model.{ExternalId, ExternalSource, Thing, ThingType}
import com.chrisbenincasa.services.teletracker.external.tmdb.TmdbClient
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.chrisbenincasa.services.teletracker.model.tmdb._
import com.chrisbenincasa.services.teletracker.process.tmdb.TmdbEntityProcessor
import com.chrisbenincasa.services.teletracker.util.TmdbMovieImporter
import com.chrisbenincasa.services.teletracker.util.json.circe._
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import io.circe.generic.auto._
import javax.inject.Inject
import shapeless.{Inl, Inr}
import scala.concurrent.{ExecutionContext, Future}

class SearchController @Inject()(
  config: TeletrackerConfig,
  thingsDbAccess: ThingsDbAccess,
  tmdbClient: TmdbClient,
  resultProcessor: TmdbEntityProcessor,
  movieImporter: TmdbMovieImporter
)(implicit executionContext: ExecutionContext) extends Controller {
  prefix("/api/v1") {
    get("/search") { req: Request =>
      val query = req.params("query")

      tmdbClient.makeRequest[SearchResult]("search/multi", Seq("query" -> query)).
        flatMap(handleSearchMultiResult).
        map(result => {
          response.ok.contentTypeJson().body(DataResponse.complex(result))
        })
    }
  }

  object extractId extends shapeless.Poly1 {
    implicit val atMovie: Case.Aux[Movie, String] = at { _.id.toString }
    implicit val atShow: Case.Aux[TvShow, String] = at { _.id.toString }
    implicit val atPerson: Case.Aux[Person, String] = at { _.id.toString }
  }

  private def handleSearchMultiResult(result: SearchResult) = {
    val movies = result.results.flatMap(_.filter[Movie]).flatMap(_.head)
    val shows = result.results.flatMap(_.filter[TvShow]).flatMap(_.head)

    val existingMovies = thingsDbAccess.findThingsByExternalIds(ExternalSource.TheMovieDb, movies.map(_.id.toString).toSet, ThingType.Movie)
    val existingShows = thingsDbAccess.findThingsByExternalIds(ExternalSource.TheMovieDb, shows.map(_.id.toString).toSet, ThingType.Show)
    val existingPeople = thingsDbAccess.findThingsByExternalIds(ExternalSource.TheMovieDb, shows.map(_.id.toString).toSet, ThingType.Person)

    def byExternalId[T](seq: Seq[(ExternalId, T)]): Map[String, T] = {
      seq.collect { case (eid, m) if eid.tmdbId.isDefined => eid.tmdbId.get -> m }.toMap
    }

    val existingMoviesByExternalId = existingMovies.map(byExternalId)
    val existingShowsByExternalId = existingShows.map(byExternalId)
    val existingPeopleByExternalId = existingPeople.map(byExternalId)

    val partitionedResults = for {
      existingM <- existingMoviesByExternalId
      existingS <- existingShowsByExternalId
      existingP <- existingPeopleByExternalId
    } yield {
      result.results.partition(result => {
        val id = result.fold(extractId)
        !existingM.isDefinedAt(id) && !existingS.isDefinedAt(id) && !existingP.isDefinedAt(id)
      })
    }

    val newlySavedByExternalId = partitionedResults.flatMap { case (missing, existing) => {
      val (missingSync, missingAsync) = missing.splitAt(5)

      val res = Future.sequence(resultProcessor.processSearchResults(missingSync ++ existing)).map(_.toMap)

      res.onComplete(_ => resultProcessor.processSearchResults(missingAsync))

      res
    } }

    for {
      existingM <- existingMoviesByExternalId
      existingS <- existingShowsByExternalId
      newlySaved <- newlySavedByExternalId
    } yield {
      result.results.flatMap(_.filterNot[Person]).collect {
        case Inl(movie) => existingM.get(movie.id.toString).orElse(newlySaved.get(movie.id.toString))
        case Inr(Inl(show)) => existingS.get(show.id.toString).orElse(newlySaved.get(show.id.toString))
        case Inr(Inr(_)) => sys.error("Impossible")
      }.flatten
    }
  }
}

