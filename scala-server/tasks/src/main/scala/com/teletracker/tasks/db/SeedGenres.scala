package com.teletracker.tasks.db

import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.inject.SyncDbProvider
import com.teletracker.common.model.tmdb.GenreListResponse
import com.teletracker.common.util.Slug
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import com.teletracker.common.util.Futures._

object SeedGenres extends TeletrackerTaskApp[GenreSeeder2]

//class GenreSeeder @Inject()(
//  tmdbClient: TmdbClient,
//  provider: DbProvider,
//  genres: Genres,
//  genreReferences: GenreReferences)
//    extends TeletrackerTask {
//  import genres.driver.api._
//
//  def run(args: Map[String, Option[Any]]): Unit = {
//    Await.result(provider.getDB.run(genres.query.delete), Duration.Inf)
//
//    val movieGenres = Await.result(
//      tmdbClient.makeRequest[GenreListResponse]("genre/movie/list"),
//      Duration.Inf
//    )
//
//    val inserts = movieGenres.genres.map {
//      case g =>
//        val gModel =
//          Genre(None, g.name, GenreType.Movie, Slug.forString(g.name))
//        val insert = (genres.query returning genres.query.map(_.id) into (
//          (
//            g,
//            id
//          ) => g.copy(id = Some(id))
//        )) += gModel
//        insert.flatMap(saved => {
//          genreReferences.query += GenreReference(
//            None,
//            ExternalSource.TheMovieDb,
//            g.id.toString,
//            saved.id.get
//          )
//        })
//    }
//
//    val movieGenreInserts = provider.getDB.run(
//      DBIO.sequence(inserts)
//    )
//
//    Await.result(movieGenreInserts, Duration.Inf)
//
//    val tvGenres = Await.result(
//      tmdbClient.makeRequest[GenreListResponse]("genre/tv/list"),
//      Duration.Inf
//    )
//
//    val tvInserts = tvGenres.genres.map {
//      case g =>
//        val gModel = Genre(None, g.name, GenreType.Tv, Slug.forString(g.name))
//        val insert = (genres.query returning genres.query.map(_.id) into (
//          (
//            g,
//            id
//          ) => g.copy(id = Some(id))
//        )) += gModel
//        insert.flatMap(saved => {
//          genreReferences.query += GenreReference(
//            None,
//            ExternalSource.TheMovieDb,
//            g.id.toString,
//            saved.id.get
//          )
//        })
//    }
//
//    val tvGenreInserts = provider.getDB.run(
//      DBIO.sequence(tvInserts)
//    )
//
//    Await.result(tvGenreInserts, Duration.Inf)
//  }
//}

class GenreSeeder2 @Inject()(
  tmdbClient: TmdbClient,
  provider: SyncDbProvider,
  genres: Genres,
  genreReferences: GenreReferences)
    extends TeletrackerTask {

  import genres.driver.api._

  val initialBoth =
    Seq(
      "Action & Adventure",
      "Animation",
      "Comedy",
      "Crime",
      "Documentary",
      "Drama",
      "Family",
      "Fantasy",
      "History",
      "Horror",
      "Kids",
      "Mystery",
      "Romance",
      "Science Fiction",
      "Thriller",
      "War",
      "Western"
    ).map(name => {
      Genre(
        None,
        name,
        Slug.forString(name),
        List(GenreType.Movie, GenreType.Tv)
      )
    })

  val initalJustMovie = Seq("Music", "Made for TV").map(name => {
    Genre(
      None,
      name,
      Slug.forString(name),
      List(GenreType.Movie)
    )
  })

  val initialJustTv = Seq("Reality", "Soap", "Talk", "Politics").map(name => {
    Genre(
      None,
      name,
      Slug.forString(name),
      List(GenreType.Tv)
    )
  })

  val tmdbMappings = Map(
    "Action & Adventure" -> Set(28, 12, 10759),
    "Animation" -> Set(16),
    "Comedy" -> Set(35),
    "Crime" -> Set(80),
    "Documentary" -> Set(99),
    "Drama" -> Set(18),
    "Family" -> Set(10751),
    "Fantasy" -> Set(14, 10765),
    "History" -> Set(36),
    "Horror" -> Set(27),
    "Kids" -> Set(10762),
    "Mystery" -> Set(9648),
    "Romance" -> Set(10749),
    "Science Fiction" -> Set(878, 10765),
    "Thriller" -> Set(53),
    "War" -> Set(10752),
    "Western" -> Set(37)
  )

  private val initialGenres =
    (initialBoth ++ initalJustMovie ++ initialJustTv).sortBy(_.name)

  override def run(args: Args): Unit = {
    val insertedGenres = provider.getDB
      .run {
        DBIO.sequence(
          initialGenres.map(genre => {
            genres.query
              .returning(genres.query.map(_.id))
              .into((g, id) => g.copy(id = Some(id))) += genre
          })
        )
      }
      .await()
      .groupBy(_.name)
      .mapValues(_.head)

    val references = tmdbMappings.flatMap {
      case (name, mappings) =>
        mappings.map(mapping => {
          GenreReference(
            None,
            ExternalSource.TheMovieDb,
            mapping.toString,
            insertedGenres(name).id.get
          )
        })
    }

    provider.getDB
      .run {
        genreReferences.query ++= references
      }
      .await()
  }
}
