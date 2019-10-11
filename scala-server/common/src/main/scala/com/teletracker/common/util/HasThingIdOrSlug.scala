package com.teletracker.common.util

import java.util.UUID

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
