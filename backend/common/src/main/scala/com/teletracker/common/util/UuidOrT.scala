package com.teletracker.common.util

import java.util.UUID

trait UuidOrT[T] {
  def id: Option[UUID] = idOrFallback.left.toOption
  def fallback: Option[T] = idOrFallback.right.toOption
  def idOrFallback: Either[UUID, T]
}

object UuidOrT {
  def unapply(arg: UuidOrT[_]): Option[Either[UUID, _]] =
    Some(arg.idOrFallback)

  def fromUUID[T](uuid: UUID): UuidOrT[T] = new UuidOrT[T] {
    override val idOrFallback: Either[UUID, T] = Left(uuid)
  }

  def fromValue[T](value: T): UuidOrT[T] = new UuidOrT[T] {
    override val idOrFallback: Either[UUID, T] = Right(value)
  }

  def parse[T](
    input: String,
    transformFallback: String => T
  ): UuidOrT[T] = {
    new UuidOrT[T] {
      override lazy val idOrFallback: Either[UUID, T] = {
        if (UuidRegex.findFirstIn(input).isDefined) {
          Left(UUID.fromString(input))
        } else {
          Right(transformFallback(input))
        }
      }
    }
  }
}
