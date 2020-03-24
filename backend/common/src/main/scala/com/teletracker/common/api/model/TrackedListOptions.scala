package com.teletracker.common.api.model

import com.teletracker.common.db.dynamo.model.UserListRowOptions

case class TrackedListOptions(removeWatchedItems: Boolean) {
  def toRow: TrackedListRowOptions = TrackedListRowOptions(removeWatchedItems)
  def toDynamoRow: UserListRowOptions = UserListRowOptions(removeWatchedItems)
}

case class TrackedListConfiguration(
  options: Option[TrackedListOptions],
  ruleConfiguration: Option[TrackedListRules])
