package com.teletracker.common.db.dynamo.model

import com.teletracker.common.db.model.DynamicListRules
import java.util.UUID

object StoredUserListFactory {
  def defaultList(userId: String): StoredUserList = {
    StoredUserList(
      UUID.randomUUID(),
      "Watchlist",
      isDefault = true,
      isPublic = false,
      userId
    )
  }

  def watchedList(userId: String): StoredUserList = {
    StoredUserList(
      UUID.randomUUID(),
      "Watched",
      isDefault = true,
      isPublic = false,
      userId,
      isDynamic = true,
      Some(DynamicListRules.watched)
    )
  }
}
