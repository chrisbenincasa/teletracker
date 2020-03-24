package com.teletracker.tasks.db.legacy_model

import com.teletracker.common.api.model.TrackedListRowOptions
import com.teletracker.common.db.model.DynamicListRules
import com.teletracker.common.util.json.circe._
import io.circe.parser._
import java.time.OffsetDateTime

object TrackedListRow {
  def fromLine(
    line: String,
    separator: Char = '\t'
  ): TrackedListRow = {
    val Array(
      id,
      name,
      isDefault,
      isPublic,
      userId,
      isDynamic,
      rules,
      options,
      deletedAt
    ) =
      line.split(separator)

    TrackedListRow(
      id = id.toInt,
      name = name,
      isDefault = isDefault.toBoolean,
      isPublic = isPublic.toBoolean,
      isDynamic = isDynamic.toBoolean,
      userId = userId,
      rules = Option(rules)
        .filter(_.nonEmpty)
        .filterNot(_ == "\\N")
        .map(decode[DynamicListRules](_).right.get),
      options = Option(options)
        .filter(_.nonEmpty)
        .filterNot(_ == "\\N")
        .map(decode[TrackedListRowOptions](_).right.get),
      deletedAt = Option(deletedAt)
        .filter(_.nonEmpty)
        .filterNot(_ == "\\N")
        .map(OffsetDateTime.parse)
    )
  }
}

case class TrackedListRow(
  id: Int,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  isDynamic: Boolean = false,
  rules: Option[DynamicListRules] = None,
  options: Option[TrackedListRowOptions] = None,
  deletedAt: Option[OffsetDateTime] = None)
