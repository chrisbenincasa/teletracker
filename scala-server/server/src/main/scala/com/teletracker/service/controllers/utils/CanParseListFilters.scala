package com.teletracker.service.controllers.utils

import com.teletracker.service.controllers.ListFilters
import com.teletracker.service.db.model.ThingType
import scala.util.Try

trait CanParseListFilters {
  def parseListFilters(itemTypes: Seq[String]): ListFilters = {
    ListFilters(
      if (itemTypes.nonEmpty) {
        Some(
          itemTypes
            .flatMap(typ => Try(ThingType.fromString(typ)).toOption)
            .toSet
        )
      } else {
        None
      }
    )
  }
}
