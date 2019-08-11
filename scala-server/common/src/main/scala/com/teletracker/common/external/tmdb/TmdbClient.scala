package com.teletracker.common.external.tmdb

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.model.tmdb.{
  Collection,
  Movie,
  MovieSearchResult,
  PagedResult,
  TmdbError,
  TvSearchResult
}
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.parser._
import com.teletracker.common.util.json.circe._
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TmdbClient @Inject()(
  config: TeletrackerConfig,
  clientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val host = "api.themoviedb.org"

  private lazy val client = {
    clientFactory.create(host, HttpClientOptions(useTls = true))
  }

  def searchMovies(query: String): Future[MovieSearchResult] = {
    makeRequest[MovieSearchResult]("search/movie", Seq("query" -> query))
  }

  def searchTv(query: String): Future[TvSearchResult] = {
    makeRequest[TvSearchResult]("search/tv", Seq("query" -> query))
  }

  def getCollection(collectionId: Int): Future[Collection] = {
    makeRequest[Collection](s"collection/$collectionId")
  }

  def getPopularMovies(): Future[PagedResult[Movie]] = {
    makeRequest[PagedResult[Movie]]("movie/popular")
  }

  def makeRequest[T](
    path: String,
    params: Seq[(String, String)] = Seq.empty
  )(implicit decoder: Decoder[T],
    executionContext: ExecutionContext
  ): Future[T] = {
    client
      .get(
        HttpRequest(
          s"/3/${path.stripPrefix("/")}",
          List("api_key" -> config.tmdb.api_key) ++ params
        )
      )
      .map(response => {
        val parsed = parse(response.content)
        parsed match {
          case Left(e) =>
            logger
              .error(s"Could not parse. Result string:\n${response.content}", e)
            throw e
          case Right(s) =>
            s.as[T] match {
              case Left(_)     => s.as[TmdbError].fold(throw _, throw _)
              case Right(json) => json
            }
        }
      })
  }
}
