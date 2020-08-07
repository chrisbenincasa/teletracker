package com.teletracker.common.util

import cats.{Eq, Monoid, MonoidK}
import cats.implicits._

object Monoidal extends Monoidal {
  def ifAnyEmpty(x: TraversableOnce[Option[_]]): Boolean = {
    x.exists(_.isEmpty)
  }

  def ifAnyDefined(x: TraversableOnce[Option[_]]): Boolean = {
    x.exists(_.isDefined)
  }
}

trait Monoidal {
  implicit def toMonoidalExtras[T: Monoid](t: T): MonoidalExtras[T] =
    new MonoidalExtras[T](t)
}

final class MonoidalExtras[T](val t: T) extends AnyVal {
  def ifEmptyOption(
    implicit M: Monoid[T],
    eq: Eq[T]
  ): Option[T] =
    if (M.isEmpty(t)) {
      None
    } else {
      Some(t)
    }
}
