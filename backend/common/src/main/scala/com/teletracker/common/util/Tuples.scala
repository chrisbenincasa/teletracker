package com.teletracker.common.util

object Tuples {
  implicit def richOptionTuple2[T, U](
    t: (Option[T], Option[U])
  ): RichOptionTuple2[T, U] = new RichOptionTuple2[T, U](t)
}

class RichOptionTuple2[T, U](val t: (Option[T], Option[U])) extends AnyVal {
  def ifBothPresent[X](f: (T, U) => X): Option[X] = {
    t match {
      case (Some(tv), Some(uv)) => Some(f(tv, uv))
      case _                    => None
    }
  }

  def bothEmpty: Boolean = t._1.isEmpty && t._2.isEmpty
}
