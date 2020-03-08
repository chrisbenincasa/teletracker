package com.teletracker.common.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

trait ClosedRange[T] {
  def start: T
  def end: T
  def inclusive: Boolean = true
}

case class ClosedDateRange(
  start: LocalDate,
  end: LocalDate,
  override val inclusive: Boolean = true)
    extends ClosedRange[LocalDate] {

  def days: List[LocalDate] = {
    val range = if (inclusive) {
      0L to start.until(end, ChronoUnit.DAYS)
    } else {
      0L until start.until(end, ChronoUnit.DAYS)
    }

    range.toList.map(start.plusDays)
  }
}
