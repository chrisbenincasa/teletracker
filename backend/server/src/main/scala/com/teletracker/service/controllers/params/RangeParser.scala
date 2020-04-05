package com.teletracker.service.controllers.params

import com.teletracker.common.util.ClosedNumericRange
import scala.util.{Failure, Success, Try}

object RangeParser {
  def parseRatingString(s: String): Option[ClosedNumericRange[Double]] = {
    if (s.isEmpty) {
      None
    } else {
      Try {
        val Array(min, max) = s.split(":", 2)
        ClosedNumericRange(
          Option(min).filter(_.nonEmpty).map(_.toDouble).getOrElse(0.0),
          Option(max).filter(_.nonEmpty).map(_.toDouble).getOrElse(10.0)
        )
      } match {
        case Failure(_)     => None
        case Success(value) => Some(value)
      }
    }
  }
}
