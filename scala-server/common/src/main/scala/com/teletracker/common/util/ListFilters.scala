package com.teletracker.common.util

import com.teletracker.common.db.model.ThingType
import scala.util.Try

case class ListFilters(itemTypes: Option[Set[ThingType]])

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
