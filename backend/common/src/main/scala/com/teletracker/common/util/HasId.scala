package com.teletracker.common.util

object HasId {
  type Aux[T, _Id] = HasId[T] { type Id = _Id }

  def instance[T, Repr](extract: T => Repr): HasId.Aux[T, Repr] = new HasId[T] {
    override type Id = Repr
    override def id(x: T): Id = extract(x)
  }
}

trait HasId[T] {
  type Id
  def id(x: T): Id
  def idString(x: T): String = id(x).toString
  def asString(id: Id): String = id.toString
}
