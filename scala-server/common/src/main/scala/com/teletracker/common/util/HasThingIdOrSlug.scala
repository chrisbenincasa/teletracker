package com.teletracker.common.util

import java.util.UUID

object IdOrSlug {
  def unapply(arg: IdOrSlug): Option[Either[UUID, Slug]] = {
    Some(arg.idOrSlug)
  }

  def fromUUID(uuid: UUID): IdOrSlug = {
    new IdOrSlug {
      override def idOrSlug: Either[UUID, Slug] = Left(uuid)
    }
  }
}

trait IdOrSlug {
  def id: Option[UUID] = idOrSlug.left.toOption
  def slug: Option[Slug] = idOrSlug.right.toOption
  def idOrSlug: Either[UUID, Slug]
}

object HasThingIdOrSlug {
  def parse(thingId: String): Either[UUID, Slug] = {
    if (UuidRegex.findFirstIn(thingId).isDefined) {
      Left(UUID.fromString(thingId))
    } else {
      Right(Slug.raw(thingId))
    }
  }

  def parseIdOrSlug(thingId: String): IdOrSlug = new IdOrSlug {
    override lazy val idOrSlug: Either[UUID, Slug] = parse(thingId)
  }
}

trait HasThingIdOrSlug {
  def thingId: String

  lazy val idOrSlug: Either[UUID, Slug] = HasThingIdOrSlug.parse(thingId)
}
