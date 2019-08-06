package com.teletracker.service.tools

import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.inject.DbProvider
import com.teletracker.common.model.tmdb.GenreListResponse
import com.teletracker.common.util.Slug
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object SeedGenres extends TeletrackerJob {
  override protected def runInternal(): Unit = {
    injector.instance[GenreSeeder].run()
  }
}

class GenreSeeder @Inject()(
  tmdbClient: TmdbClient,
  provider: DbProvider,
  genres: Genres,
  genreReferences: GenreReferences) {
  import genres.driver.api._

  def run(): Unit = {
    Await.result(provider.getDB.run(genres.query.delete), Duration.Inf)

    val movieGenres = Await.result(
      tmdbClient.makeRequest[GenreListResponse]("genre/movie/list"),
      Duration.Inf
    )

    val inserts = movieGenres.genres.map {
      case g =>
        val gModel =
          Genre(None, g.name, GenreType.Movie, Slug.forString(g.name))
        val insert = (genres.query returning genres.query.map(_.id) into (
          (
            g,
            id
          ) => g.copy(id = Some(id))
        )) += gModel
        insert.flatMap(saved => {
          genreReferences.query += GenreReference(
            None,
            ExternalSource.TheMovieDb,
            g.id.toString,
            saved.id.get
          )
        })
    }

    val movieGenreInserts = provider.getDB.run(
      DBIO.sequence(inserts)
    )

    Await.result(movieGenreInserts, Duration.Inf)

    val tvGenres = Await.result(
      tmdbClient.makeRequest[GenreListResponse]("genre/tv/list"),
      Duration.Inf
    )

    val tvInserts = tvGenres.genres.map {
      case g =>
        val gModel = Genre(None, g.name, GenreType.Tv, Slug.forString(g.name))
        val insert = (genres.query returning genres.query.map(_.id) into (
          (
            g,
            id
          ) => g.copy(id = Some(id))
        )) += gModel
        insert.flatMap(saved => {
          genreReferences.query += GenreReference(
            None,
            ExternalSource.TheMovieDb,
            g.id.toString,
            saved.id.get
          )
        })
    }

    val tvGenreInserts = provider.getDB.run(
      DBIO.sequence(tvInserts)
    )

    Await.result(tvGenreInserts, Duration.Inf)
  }
}
