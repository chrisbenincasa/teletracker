package com.teletracker.common.db.model

import java.util.UUID

case class UserThingTag(
  id: Int,
  userId: String,
  thingId: UUID,
  action: UserThingTagType,
  value: Option[Double])
