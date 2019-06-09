package com.teletracker.service.util

object Functions {
  implicit def anyToRichAny[T](v: T): RichAny[T] = new RichAny[T](v)
}

final class RichAny[T](val v: T) extends AnyVal {
  def applyIf(cond: => Boolean)(f: T => T): T = if (cond) f(v) else v
}
