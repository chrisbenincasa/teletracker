package com.teletracker.common.util

import scala.util.Try

object HasGenreIdOrSlug {
  def parse(genreId: String): Either[Int, Slug] = {
    Try(genreId.toInt).map(Left(_)).getOrElse(Right(Slug.raw(genreId)))
  }
}
