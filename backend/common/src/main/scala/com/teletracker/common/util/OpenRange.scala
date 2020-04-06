package com.teletracker.common.util

import java.time.LocalDate

trait OpenRange[T] {
  def start: Option[T]
  def end: Option[T]
  def inclusive: Boolean = true

  def isFinite: Boolean = start.isDefined || end.isDefined
}

object OpenDateRange {
  def forYearRange(
    start: Option[Int],
    end: Option[Int]
  ): OpenDateRange = {
    OpenDateRange(start.map(localDateAtYear), end.map(localDateAtYear))
  }

  private def localDateAtYear(year: Int): LocalDate = LocalDate.of(year, 1, 1)
}

case class OpenDateRange(
  start: Option[LocalDate],
  end: Option[LocalDate],
  override val inclusive: Boolean = true)
    extends OpenRange[LocalDate]

case class OpenNumericRange[T: Numeric](
  start: Option[T],
  end: Option[T])
    extends OpenRange[T]
