package com.teletracker.common.util

object HasId {
  type Aux[T, _Id] = HasId[T] { type Id = _Id }
}

trait HasId[T] {
  type Id
  def id(x: T): Id
  def idString(x: T): String = id(x).toString
  def asString(id: Id): String = id.toString
}
