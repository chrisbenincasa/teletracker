package com.teletracker.common.db.util

import shapeless.PolyDefns.->
import slick.lifted.{CanBeQueryCondition, ColumnOrdered, Query, Rep}

/**
  * Optionally filter on a column with a supplied predicate
  *
  * @param query The initial query on which the filters must be applied
  */
case class InhibitFilter[Table, Row, C[_]](query: Query[Table, Row, C]) {

  /**
    * Filter with predicate `pred` only if `data` is defined
    */
  def filter[Data, Result <: Rep[_]](
    data: Option[Data]
  )(
    pred: Data => Table => Result
  )(implicit wr: CanBeQueryCondition[Result]
  ): InhibitFilter[Table, Row, C] = {
    data match {
      case Some(value) => InhibitFilter(query.filter(pred(value)))
      case None        => this
    }
  }

  def sort[Data, Result <: ColumnOrdered[_]](
    data: Option[Data]
  )(
    pred: Data => Table => Result
  ): InhibitFilter[Table, Row, C] = {
    data match {
      case Some(value) => InhibitFilter(query.sortBy(pred(value)))
      case None        => this
    }
  }

  def cond[Data, Result <: Rep[_]](
    cond: => Boolean
  )(
    pred: Table => Result
  )(implicit wr: CanBeQueryCondition[Result]
  ): InhibitFilter[Table, Row, C] = {
    if (cond) {
      InhibitFilter(query.filter(pred))
    } else {
      this
    }
  }
}
