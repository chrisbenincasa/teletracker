package com.teletracker.service.util

object Lists {
  implicit def toSafeTakeList[T](l: List[T]): ListWithSafeTake[T] =
    new ListWithSafeTake[T](l)
}

class ListWithSafeTake[T](val l: List[T]) extends AnyVal {
  def safeTake(n: Int): List[T] = if (n < 0) l else l.take(n)
}
