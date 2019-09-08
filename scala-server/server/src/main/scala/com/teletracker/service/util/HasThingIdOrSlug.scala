package com.teletracker.service.util

import com.teletracker.common.util.Slug
import java.util.UUID
import scala.util.Try

object HasThingIdOrSlug {
  def parse(thingId: String): Either[UUID, Slug] = {
    if (UuidRegex.findFirstIn(thingId).isDefined) {
      Left(UUID.fromString(thingId))
    } else {
      Right(Slug.raw(thingId))
    }
  }
}

trait HasThingIdOrSlug {
  def thingId: String

  lazy val idOrSlug: Either[UUID, Slug] = HasThingIdOrSlug.parse(thingId)
}

object HasGenreIdOrSlug {
  def parse(genreId: String): Either[Int, Slug] = {
    Try(genreId.toInt).map(Left(_)).getOrElse(Right(Slug.raw(genreId)))
  }
}
