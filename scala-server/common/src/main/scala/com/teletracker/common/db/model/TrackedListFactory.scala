package com.teletracker.common.db.model

object TrackedListFactory {
  def defaultList(userId: String): TrackedListRow = {
    TrackedListRow(
      -1,
      "Default List",
      isDefault = true,
      isPublic = false,
      userId
    )
  }

  def watchedList(userId: String): TrackedListRow = {
    TrackedListRow(
      -1,
      "Watched",
      isDefault = true,
      isPublic = false,
      userId,
      isDynamic = true,
      Some(DynamicListRules.watched)
    )
  }
}