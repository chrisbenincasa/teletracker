package com.teletracker.common.util

object Lists {
  implicit def toSafeTakeList[T](l: List[T]): ListWithSafeTake[T] =
    new ListWithSafeTake[T](l)

  implicit def toSafeTakeIterator[T](l: Iterator[T]): IteratorWithSafeTake[T] =
    new IteratorWithSafeTake[T](l)

  implicit def toStreamTakeIterator[T](l: Stream[T]): StreamWithSafeTake[T] =
    new StreamWithSafeTake(l)
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
