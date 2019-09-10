package com.teletracker.common.util.json.circe

import com.teletracker.common.model.tmdb.Genre
import io.circe.Json
import monocle.Optional

object Paths {
  import io.circe.optics.JsonPath._
  final val MovieGenres = root.themoviedb.movie.genres.as[List[Genre]]
  final val ShowGenres = root.themoviedb.show.genres.as[List[Genre]]

  final val MovieReleaseDate = root.themoviedb.movie.genres.as[String]
  final val ShowFirstAirDate = root.themoviedb.show.genres.as[String]

  def applyPaths[T](
    json: Json,
    paths: monocle.Optional[Json, T]*
  ): Option[T] = {
    paths.toStream.map(_.getOption(json)).collectFirst {
      case Some(t) => t
    }
  }

  def releaseDate(json: Json) =
    applyPaths(json, MovieReleaseDate, ShowFirstAirDate)
}
