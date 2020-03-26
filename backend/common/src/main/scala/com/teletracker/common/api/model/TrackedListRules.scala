package com.teletracker.common.api.model

import com.teletracker.common.db.model.{
  DynamicListDefaultSort,
  DynamicListGenreRule,
  DynamicListItemTypeRule,
  DynamicListNetworkRule,
  DynamicListPersonRule,
  DynamicListReleaseYearRule,
  DynamicListRule,
  DynamicListRules,
  DynamicListTagRule,
  ItemType,
  PersonAssociationType,
  UserThingTagType
}
import java.util.UUID

object TrackedListRules {
  private def convertRule(trackedListRule: TrackedListRule): DynamicListRule = {
    trackedListRule match {
      case TrackedListPersonRule(personId, associationType) =>
        DynamicListPersonRule(personId, associationType)

      case TrackedListTagRule(tagType, value, isPresent) =>
        DynamicListTagRule(tagType, value, isPresent)

      case TrackedListGenreRule(genreId) =>
        DynamicListGenreRule(genreId)

      case TrackedListItemTypeRule(itemType) =>
        DynamicListItemTypeRule(itemType)

      case TrackedListNetworkRule(networkId) =>
        DynamicListNetworkRule(networkId)

      case TrackedListReleaseYearRule(minimum, maximum) =>
        DynamicListReleaseYearRule(minimum, maximum)
    }
  }
}

case class TrackedListRules(
  rules: List[TrackedListRule],
  sortOptions: Option[TrackedListSortOptions]) {
  require(rules.nonEmpty)
  def toRow: DynamicListRules = {
    DynamicListRules(
      rules = rules.map(TrackedListRules.convertRule),
      sort = sortOptions.map(opts => DynamicListDefaultSort(opts.sort))
    )
  }
}

case class TrackedListSortOptions(sort: String)

sealed trait TrackedListRule

case class TrackedListTagRule(
  tagType: UserThingTagType,
  value: Option[Double],
  isPresent: Option[Boolean])
    extends TrackedListRule

case class TrackedListPersonRule(
  personId: UUID,
  associationType: Option[PersonAssociationType])
    extends TrackedListRule

case class TrackedListGenreRule(genreId: Int) extends TrackedListRule

case class TrackedListItemTypeRule(itemType: ItemType) extends TrackedListRule

case class TrackedListNetworkRule(networkId: Int) extends TrackedListRule

case class TrackedListReleaseYearRule(
  minimum: Option[Int],
  maximum: Option[Int])
    extends TrackedListRule
