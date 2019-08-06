package com.teletracker.common.util

import com.teletracker.common.model.tmdb.Movie
import java.time.LocalDate

object Movies {
  implicit def toRichMovie(m: Movie): RichMovie = new RichMovie(m)
}

class RichMovie(val movie: Movie) extends AnyVal {
  def releaseYear: Option[Int] =
    movie.release_date
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_).getYear)
}
