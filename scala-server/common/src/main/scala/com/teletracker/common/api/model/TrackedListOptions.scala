package com.teletracker.common.api.model

import com.teletracker.common.db.model.{DynamicListRules, TrackedListRowOptions}

case class TrackedListOptions(removeWatchedItems: Boolean) {
  def toRow: TrackedListRowOptions = TrackedListRowOptions(removeWatchedItems)
}

object TrackedListOptions {
  def fromRow(options: TrackedListRowOptions): TrackedListOptions = {
    TrackedListOptions(options.removeWatchedItems)
  }
}

case class TrackedListConfiguration(
  options: Option[TrackedListOptions],
  ruleConfiguration: Option[TrackedListRules])

object TrackedListConfiguration {
  def fromRow(
    rules: Option[DynamicListRules],
    options: Option[TrackedListRowOptions]
  ): TrackedListConfiguration = {
    TrackedListConfiguration(
      ruleConfiguration = rules.map(TrackedListRules.fromRow),
      options = options.map(TrackedListOptions.fromRow)
    )
  }
}
