package com.teletracker.common.util.json.circe

import com.teletracker.common.model.tmdb.Genre

object Paths {
  import io.circe.optics.JsonPath._
  final val MovieGenres = root.themoviedb.movie.genres.as[List[Genre]]
  final val ShowGenres = root.themoviedb.show.genres.as[List[Genre]]
}
