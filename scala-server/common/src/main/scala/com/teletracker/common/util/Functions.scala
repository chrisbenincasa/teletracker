package com.teletracker.common.util

object Functions {
  implicit def anyToRichAny[T](v: T): RichAny[T] = new RichAny[T](v)
}

final class RichAny[T](val v: T) extends AnyVal {
  def applyIf(cond: => Boolean)(f: T => T): T = if (cond) f(v) else v
  def applyOptional[U](opt: Option[U])(f: (T, U) => T): T = opt match {
    case Some(value) => f(v, value)
    case None        => v
  }
  def through(f: T => T): T = f(v)
}
