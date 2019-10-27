package com.teletracker.common.util

import java.time.LocalDate

trait OpenRange[T] {
  def start: Option[T]
  def end: Option[T]
  def inclusive: Boolean = true

  def isFinite: Boolean = start.isDefined || end.isDefined
}

case class OpenDateRange(
  start: Option[LocalDate],
  end: Option[LocalDate],
  override val inclusive: Boolean = true)
    extends OpenRange[LocalDate]
