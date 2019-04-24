package com.chrisbenincasa.services.teletracker.controllers.utils

import com.chrisbenincasa.services.teletracker.util.Field
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
