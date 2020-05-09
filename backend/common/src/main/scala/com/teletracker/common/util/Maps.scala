package com.teletracker.common.util

object Maps {
  implicit def toRichMap[K, V](l: Map[K, V]): RichMap[K, V] =
    new RichMap[K, V](l)
}

class RichMap[K, V](val l: Map[K, V]) extends AnyVal {
  def reverse: Map[V, Set[K]] = {
    l.toList
      .groupBy(_._2)
      .map {
        case (v, tups) => v -> tups.map(_._1).toSet
      }
  }
}
