package com.teletracker.service.controllers.utils

import com.teletracker.service.util.Field
import com.twitter.finatra.http.Controller

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait CanParseFieldFilter { self: Controller =>
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
