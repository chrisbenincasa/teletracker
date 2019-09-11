package com.teletracker.common.api.model

import com.teletracker.common.db.model.{
  DynamicListPersonRule,
  DynamicListRule,
  DynamicListRules,
  DynamicListTagRule,
  UserThingTagType
}
import java.util.UUID

object TrackedListRules {
  def fromRow(dynamicListRules: DynamicListRules): TrackedListRules = {
    TrackedListRules(
      rules = dynamicListRules.rules.map(convertRule)
    )
  }

  private def convertRule(dynamicListRule: DynamicListRule): TrackedListRule = {
    dynamicListRule match {
      case DynamicListPersonRule(personId, _) =>
        TrackedListPersonRule(personId)

      case DynamicListTagRule(tagType, value, isPresent, _) =>
        TrackedListTagRule(tagType, value, isPresent)
    }
  }

  private def convertRule(trackedListRule: TrackedListRule): DynamicListRule = {
    trackedListRule match {
      case TrackedListPersonRule(personId) =>
        DynamicListPersonRule(personId)

      case TrackedListTagRule(tagType, value, isPresent) =>
        DynamicListTagRule(tagType, value, isPresent)
    }
  }
}

case class TrackedListRules(rules: List[TrackedListRule]) {
  require(rules.nonEmpty)
  def toRow: DynamicListRules = {
    DynamicListRules(
      rules = rules.map(TrackedListRules.convertRule)
    )
  }
}

sealed trait TrackedListRule

case class TrackedListTagRule(
  tagType: UserThingTagType,
  value: Option[Double],
  isPresent: Option[Boolean])
    extends TrackedListRule

case class TrackedListPersonRule(personId: UUID) extends TrackedListRule
