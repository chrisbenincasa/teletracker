package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.{model, MetadataDbAccess}
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Slug
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GenreSeeder @Inject()(
  tmdbClient: TmdbClient,
  metadataDbAccess: MetadataDbAccess
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

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
    ).zipWithIndex.map {
      case (name, idx) =>
        model.StoredGenre(
          0,
          name,
          Slug.forString(name),
          Set(GenreType.Movie, GenreType.Tv)
        )
    }

  val initalJustMovie = Seq("Music", "Made for TV").map(name => {
    model.StoredGenre(
      0,
      name,
      Slug.forString(name),
      Set(GenreType.Movie)
    )
  })

  val initialJustTv =
    Seq("Reality", "Soap", "Talk", "Politics", "News").map(name => {
      model.StoredGenre(
        0,
        name,
        Slug.forString(name),
        Set(GenreType.Tv)
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
    "War" -> Set(10752, 10768),
    "Western" -> Set(37),
    // Tv Only
    "Made for TV" -> Set(10770),
    "News" -> Set(10763),
    "Reality" -> Set(10764),
    "Soap" -> Set(10766),
    "Talk" -> Set(10767),
    "Politics" -> Set(10768)
  )

  private val initialGenres =
    (initialBoth ++ initalJustMovie ++ initialJustTv)
      .sortBy(_.name)
      .zipWithIndex
      .map {
        case (genre, idx) =>
          genre.copy(id = idx + 1)
      }

  override def runInternal(args: Args): Unit = {
    val insertedGenres = Future
      .sequence(initialGenres.map(metadataDbAccess.saveGenre))
      .map(_.groupBy(_.name).mapValues(_.head))
      .await()

    val references = tmdbMappings.flatMap {
      case (name, mappings) =>
        mappings.map(mapping => {
          model.StoredGenreReference(
            ExternalSource.TheMovieDb,
            mapping.toString,
            insertedGenres(name).id
          )
        })
    }

    Future.sequence(references.map(metadataDbAccess.saveGenreReference)).await()
  }
}
