package com.teletracker.common.util

import com.twitter.util.logging.Logging
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait CanParseFieldFilter { self: Logging =>
  def parseFieldsOrNone(fieldString: Option[String]): Option[List[Field]] = {
    fieldString.flatMap(fields => {
      Field.parse(fields) match {
        case Success(value) => Some(value)
        case Failure(NonFatal(e)) =>
          logger.error(s"Unable to parse fields string: $fields", e)
          None
      }
    })
  }
}
