package com.teletracker.service.db.model

object TrackedListFactory {
  def defaultList(userId: Int): TrackedListRow = {
    TrackedListRow(
      -1,
      "Default List",
      isDefault = true,
      isPublic = false,
      userId
    )
  }

  def watchedList(userId: Int): TrackedListRow = {
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
