package com.teletracker.common.db.model

import com.teletracker.common.api.model.TrackedList
import java.time.OffsetDateTime

case class TrackedListRow(
  id: Int,
  name: String,
  isDefault: Boolean,
  isPublic: Boolean,
  userId: String,
  isDynamic: Boolean = false,
  rules: Option[DynamicListRules] = None,
  options: Option[TrackedListRowOptions] = None,
  deletedAt: Option[OffsetDateTime] = None) {
  def toFull: TrackedList = TrackedList.fromRow(this)
}

case class TrackedListRowOptions(removeWatchedItems: Boolean)
