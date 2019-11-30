package com.teletracker.common.util

import java.time.LocalDate
import scala.math.Ordering.OptionOrdering

object Lists {
  implicit def toSafeTakeList[T](l: List[T]): ListWithSafeTake[T] =
    new ListWithSafeTake[T](l)

  implicit def toSafeTakeIterator[T](l: Iterator[T]): IteratorWithSafeTake[T] =
    new IteratorWithSafeTake[T](l)

  implicit def toStreamTakeStream[T](l: Stream[T]): StreamWithSafeTake[T] =
    new StreamWithSafeTake(l)

  implicit private val LocalDateOrdering: Ordering[LocalDate] =
    Ordering.fromLessThan(_.isBefore(_))

  def NullsLastOrdering[T](implicit ord: Ordering[T]): Ordering[Option[T]] =
    new OptionOrdering[T] {
      override def optionOrdering: Ordering[T] = ord
      override def compare(
        x: Option[T],
        y: Option[T]
      ) = (x, y) match {
        case (None, None)       => 0
        case (None, _)          => 1
        case (_, None)          => -1
        case (Some(x), Some(y)) => optionOrdering.compare(x, y)
      }
    }
}

class ListWithSafeTake[T](val l: List[T]) extends AnyVal {
  def safeTake(n: Int): List[T] = if (n < 0) l else l.take(n)
}

class IteratorWithSafeTake[T](val l: Iterator[T]) extends AnyVal {
  def safeTake(n: Int): Iterator[T] = if (n < 0) l else l.take(n)
}

class StreamWithSafeTake[T](val l: Stream[T]) extends AnyVal {
  def safeTake(n: Int): Stream[T] = if (n < 0) l else l.take(n)
}
