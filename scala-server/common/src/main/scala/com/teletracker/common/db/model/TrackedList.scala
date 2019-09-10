package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID

case class TrackedListRow(
  id: Int,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  isDynamic: Boolean = false,
  rules: Option[DynamicListRules] = None,
  deletedAt: Option[OffsetDateTime] = None) {
  def toFull: TrackedList = TrackedList.fromRow(this)
}

object TrackedList {
  def fromRow(row: TrackedListRow): TrackedList = {
    TrackedList(
      row.id,
      row.name,
      row.isDefault,
      row.isPublic,
      row.userId,
      things = None,
      isDynamic = row.isDynamic,
      rules = row.rules,
      deletedAt = row.deletedAt
    )
  }
}

case class TrackedList(
  id: Int,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  things: Option[List[PartialThing]] = None,
  isDynamic: Boolean = false,
  rules: Option[DynamicListRules] = None,
  isDeleted: Boolean = false,
  deletedAt: Option[OffsetDateTime] = None,
  thingCount: Option[Int] = None) {
  def withThings(things: List[PartialThing]): TrackedList = {
    this.copy(things = Some(things))
  }

  def withCount(count: Int): TrackedList = this.copy(thingCount = Some(count))
}

object DynamicListTagRule {
  def ifPresent(tagType: UserThingTagType): DynamicListTagRule =
    DynamicListTagRule(tagType, None, Some(true))
}

sealed trait DynamicListRule {
  def negated: Option[Boolean]
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
  negated: Option[Boolean] = None)
    extends DynamicListRule

object DynamicListRules {
  def watched =
    DynamicListRules(
      rules = DynamicListTagRule.ifPresent(UserThingTagType.Watched) :: Nil
    )

  def person(id: UUID) =
    DynamicListRules(
      rules = DynamicListPersonRule(id) :: Nil
    )
}

// TODO: Insanely simple ruleset where rules are AND'd together. Expand to be more flexible.
case class DynamicListRules(rules: List[DynamicListRule]) {
  require(rules.nonEmpty)
}

class TrackedLists @Inject()(
  val driver: CustomPostgresProfile,
  dbImplicits: DbImplicits) {
  import dbImplicits._
  import driver.api._

  class TrackedListsTable(tag: Tag)
      extends Table[TrackedListRow](tag, "lists") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def isDefault = column[Boolean]("is_default", O.Default(false))
    def isPublic = column[Boolean]("is_public", O.Default(true))
    def createdAt = column[java.sql.Timestamp]("created_at")
    def lastUpdatedAt = column[java.sql.Timestamp]("last_updated_at")
    def userId = column[String]("user_id")
    def isDynamic = column[Boolean]("is_dynamic", O.Default(false))
    def rules = column[Option[DynamicListRules]]("rules", O.SqlType("jsonb"))
    def deletedAt =
      column[Option[OffsetDateTime]](
        "deleted_at",
        O.SqlType("timestamp with timezone")
      )

    override def * =
      (
        id,
        name,
        isDefault,
        isPublic,
        userId,
        isDynamic,
        rules,
        deletedAt
      ) <> (TrackedListRow.tupled, TrackedListRow.unapply)
  }

  val query = TableQuery[TrackedListsTable]

  val findSpecificListQuery = (userId: Rep[String], listId: Rep[Int]) =>
    Compiled(
      query.filter(
        list =>
          list.id === listId && list.userId === userId && list.deletedAt.isEmpty
      )
    )
}
