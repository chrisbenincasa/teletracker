package com.teletracker.common.db.model

import java.time.OffsetDateTime
import java.util.UUID

case class TrackedListThing(
  listId: Int,
  thingId: UUID,
  addedAt: OffsetDateTime,
  removedAt: Option[OffsetDateTime])
