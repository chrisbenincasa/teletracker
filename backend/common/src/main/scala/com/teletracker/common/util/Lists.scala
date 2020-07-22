package com.teletracker.common.util

import java.time.LocalDate
import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.math.Ordering.OptionOrdering

object Lists {
  implicit def toRichList[T](l: List[T]): RichList[T] =
    new RichList[T](l)

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

class RichList[T](val l: List[T]) extends AnyVal {
  def replaceWhere(
    f: T => Boolean,
    replace: T => T
  ): List[T] = {
    l.foldRight(List.empty[T]) {
      case (item, acc) if f(item) => acc :+ replace(item)
      case (item, acc)            => acc :+ item
    }
  }

  def distinctBy[U](f: T => U): Map[U, T] = {
    l.groupBy(f).mapValues(_.head).toMap
  }
}

class ListWithSafeTake[T](val l: List[T]) extends AnyVal {
  def safeTake(n: Int): List[T] = if (n < 0) l else l.take(n)
}

class TraversableWithSafeTake[T, Coll[_] <: Traversable[T]](val l: Coll[T])
    extends AnyVal {
  def safeTake(
    n: Int
  )(implicit cbf: CanBuildFrom[Coll[T], T, Coll[T]]
  ): Coll[T] = {
    if (n < 0) {
      l
    } else {
      val builder = cbf()
      builder.sizeHint(if (n < 0) l.size else n)
      l.take(n).foreach(builder += _)
      builder.result()
    }
  }
}

class IteratorWithSafeTake[T](val l: Iterator[T]) extends AnyVal {
  def safeTake(n: Int): Iterator[T] = if (n < 0) l else l.take(n)
}

class StreamWithSafeTake[T](val l: Stream[T]) extends AnyVal {
  def safeTake(n: Int): Stream[T] = if (n < 0) l else l.take(n)

  def indexFilterMod(
    mod: Int,
    band: Int
  ): Stream[T] = l.zipWithIndex.collect {
    case (t, idx) if idx % mod == band => t
  }

  def valueFilterMod(
    f: PartialFunction[T, Int],
    mod: Int,
    band: Int
  ) =
    l.collect {
      case t if f.isDefinedAt(t) && f(t) % mod == band => t
    }
}
