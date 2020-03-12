package com.teletracker.common.util

import scala.collection.generic.CanBuildFrom

object Folds {
  def list2Empty[T, U]: (List[T], List[U]) = (List.empty[T], List.empty[U])

  def fold2Append[
    T,
    U
  ]: ((List[T], List[U]), (List[T], List[U])) => (List[T], List[U]) =
    (acc: (List[T], List[U]), curr: (List[T], List[U])) =>
      (acc, curr) match {
        case ((al, ar), (l, r)) => (al ++ l) -> (ar ++ r)
      }

  def foldMapAppend[Key, Value, Collection[Value] <: Traversable[Value]](
    implicit cbf: CanBuildFrom[Collection[Value], Value, Collection[Value]]
  ) =
    (acc: Map[Key, Iterable[Value]], curr: Map[Key, Iterable[Value]]) => {
      (acc.keySet ++ curr.keySet)
        .map(key => {
          val newValue = cbf()
          val accumValue = acc.get(key)
          val currValue = curr.get(key)

          accumValue.foreach(_.foreach(newValue += _))
          currValue.foreach(_.foreach(newValue += _))

          key -> newValue.result()
        })
        .toMap
    }
}
