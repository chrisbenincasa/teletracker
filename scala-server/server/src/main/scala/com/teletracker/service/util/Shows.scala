package com.teletracker.service.util

import com.teletracker.service.model.tmdb.TvShow
import java.time.LocalDate

object Shows {
  implicit def toRichShow(m: TvShow): RichShow = new RichShow(m)
}

class RichShow(val show: TvShow) extends AnyVal {
  def releaseYear: Option[Int] =
    show.first_air_date
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_).getYear)
}
