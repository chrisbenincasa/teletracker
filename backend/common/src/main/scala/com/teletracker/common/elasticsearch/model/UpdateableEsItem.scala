package com.teletracker.common.elasticsearch.model

trait UpdateableEsItem[T] {
  type Out
  def to(t: T): Out
}

object UpdateableEsItem {
  type Aux[_T, _Out] = UpdateableEsItem[_T] { type Out = _Out }

  object syntax extends UpdateableEsItemOps
}

trait UpdateableEsItemOps {
  implicit def updateableSyntax[T](t: T): UpdateableEsItemSyntax[T] =
    new UpdateableEsItemSyntax[T](t)
}

final class UpdateableEsItemSyntax[T](val underlying: T) extends AnyVal {
  def toUpdateable(
    implicit updateableEsItem: UpdateableEsItem[T]
  ): updateableEsItem.Out = updateableEsItem.to(underlying)
}
