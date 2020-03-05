package com.teletracker.common.api.model

import com.teletracker.common.db.model.{PartialThing, TrackedListRow}
import java.time.OffsetDateTime

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
      configuration = TrackedListConfiguration.fromRow(row.rules, row.options),
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
  configuration: TrackedListConfiguration,
  things: Option[List[PartialThing]] = None,
  isDynamic: Boolean = false,
  isDeleted: Boolean = false,
  deletedAt: Option[OffsetDateTime] = None,
  thingCount: Option[Int] = None) {
  def withThings(things: List[PartialThing]): TrackedList = {
    this.copy(things = Some(things))
  }

  def withCount(count: Int): TrackedList = this.copy(thingCount = Some(count))
}
