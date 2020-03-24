package com.teletracker.common.db.model

import java.util.UUID

sealed trait DynamicListRule {
  def negated: Option[Boolean]
}

object DynamicListTagRule {
  def ifPresent(tagType: UserThingTagType): DynamicListTagRule =
    DynamicListTagRule(tagType, None, Some(true))

  def watched = ifPresent(UserThingTagType.Watched)
  def notWatched = watched.negate
}

case class DynamicListTagRule(
  tagType: UserThingTagType,
  value: Option[Double],
  isPresent: Option[Boolean],
  negated: Option[Boolean] = None)
    extends DynamicListRule {
  def withValue(value: Double): DynamicListTagRule =
    this.copy(value = Some(value))

  def negate: DynamicListTagRule = this.copy(negated = Some(true))
}

case class DynamicListPersonRule(
  personId: UUID,
  associationType: Option[PersonAssociationType],
  negated: Option[Boolean] = None)
    extends DynamicListRule

case class DynamicListGenreRule(
  genreId: Int,
  negated: Option[Boolean] = None)
    extends DynamicListRule

case class DynamicListItemTypeRule(
  itemType: ItemType,
  negated: Option[Boolean] = None)
    extends DynamicListRule

case class DynamicListNetworkRule(
  networkId: Int,
  negated: Option[Boolean] = None)
    extends DynamicListRule

sealed trait DynamicListRangeRule[T] extends DynamicListRule {
  def minimum: Option[T]
  def maximum: Option[T]
  def inclusive: Boolean = true
}

case class DynamicListReleaseYearRule(
  minimum: Option[Int],
  maximum: Option[Int],
  negated: Option[Boolean] = None)
    extends DynamicListRangeRule[Int]

case class DynamicListDefaultSort(sort: String)

case class DynamicListRules(
  rules: List[DynamicListRule],
  sort: Option[DynamicListDefaultSort]) {
  require(rules.nonEmpty)
}

object DynamicListRules {
  def watched =
    DynamicListRules(
      rules = DynamicListTagRule.ifPresent(UserThingTagType.Watched) :: Nil,
      sort = None
    )

  def person(
    id: UUID,
    associationType: Option[PersonAssociationType] = None
  ) =
    DynamicListRules(
      rules = DynamicListPersonRule(id, associationType) :: Nil,
      sort = None
    )
}
