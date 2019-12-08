package com.teletracker.common.db.model

case class Event(
  id: Option[Int],
  `type`: String,
  targetEntityType: String,
  targetEntityId: String,
  details: Option[String],
  userId: String,
  timestamp: java.sql.Timestamp) {
  def withTarget(thing: Thing): EventWithTarget =
    EventWithTarget(this, Some(thing.toPartial))
  def withTarget(thing: PartialThing): EventWithTarget =
    EventWithTarget(this, Some(thing))
}

case class EventWithTarget(
  event: Event,
  target: Option[PartialThing])
