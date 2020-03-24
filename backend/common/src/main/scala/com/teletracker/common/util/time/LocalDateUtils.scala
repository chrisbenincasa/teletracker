package com.teletracker.common.util.time

import java.time.LocalDate
import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField.{DAY_OF_MONTH, MONTH_OF_YEAR, YEAR}
import scala.util.Try

object LocalDateUtils {
  def localDateAtYear(year: Int): LocalDate = LocalDate.of(year, 1, 1)

  final val SingleMonthFallbackFormatter = new DateTimeFormatterBuilder()
    .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(MONTH_OF_YEAR, 1)
    .appendLiteral('-')
    .appendValue(DAY_OF_MONTH, 2)
    .toFormatter()

  def parseLocalDateWithFallback(str: String): Option[LocalDate] =
    Try(LocalDate.parse(str)).recoverWith {
      case _ => Try(LocalDate.parse(str, SingleMonthFallbackFormatter))
    }.toOption
}
