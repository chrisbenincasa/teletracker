package com.teletracker.common.util

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
}
