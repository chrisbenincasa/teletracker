package com.teletracker.common.util

object Numbers extends Numbers

trait Numbers {
  implicit def toRangeNumeric[T: Numeric](t: T) = new RangeBuilderNumeric[T](t)
}

final class RangeBuilderNumeric[T](val t: T) extends AnyVal {
  def +-(r: T)(implicit num: Numeric[T]): ClosedNumericRange[T] = {
    ClosedNumericRange(num.minus(t, r), num.plus(t, r))
  }
}
