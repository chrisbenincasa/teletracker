package com.teletracker.common.util.time

import java.time.LocalDate

object LocalDateUtils {
  def localDateAtYear(year: Int): LocalDate = LocalDate.of(year, 1, 1)
}
