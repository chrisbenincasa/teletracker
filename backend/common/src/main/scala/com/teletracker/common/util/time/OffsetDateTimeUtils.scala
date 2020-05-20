package com.teletracker.common.util.time

import io.circe.{Codec, Decoder, Encoder}
import java.time.OffsetDateTime
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField.{DAY_OF_MONTH, MONTH_OF_YEAR, YEAR}

object OffsetDateTimeUtils {
  final val SignedFormatter =
    new DateTimeFormatterBuilder().parseCaseInsensitive
      .append(
        new DateTimeFormatterBuilder().parseCaseInsensitive
          .append(
            new DateTimeFormatterBuilder()
              .appendValue(YEAR, 4, 10, SignStyle.ALWAYS)
              .appendLiteral('-')
              .appendValue(MONTH_OF_YEAR, 2)
              .appendLiteral('-')
              .appendValue(DAY_OF_MONTH, 2)
              .toFormatter()
          )
          .appendLiteral('T')
          .append(DateTimeFormatter.ISO_LOCAL_TIME)
          .toFormatter()
      )
      .appendOffsetId
      .toFormatter()
}
