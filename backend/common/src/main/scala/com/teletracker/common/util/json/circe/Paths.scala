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

  final val MovieVoteAverage = root.themoviedb.movie.vote_average.as[Double]
  final val ShowVoteAverage = root.themoviedb.show.vote_average.as[Double]

  final val MovieVoteCount = root.themoviedb.movie.vote_count.as[Long]
  final val ShowVoteCount = root.themoviedb.show.vote_count.as[Long]

  def applyPaths[T](
    json: Json,
    paths: monocle.Optional[Json, T]*
  ): Option[T] = {
    paths.toStream.map(_.getOption(json)).collectFirst {
      case Some(t) => t
    }
  }

  def releaseDate(json: Json): Option[String] =
    applyPaths(json, MovieReleaseDate, ShowFirstAirDate)

  def voteCount(json: Json): Option[Long] =
    applyPaths(json, MovieVoteCount, ShowVoteCount)

  def voteAverage(json: Json): Option[Double] =
    applyPaths(json, MovieVoteAverage, ShowVoteAverage)
}
