package com.teletracker.common.db.model

import com.google.inject.Provider
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class Event(
  id: Option[Int],
  `type`: String,
  targetEntityType: String,
  targetEntityId: String,
  details: Option[String],
  userId: Int,
  timestamp: java.sql.Timestamp) {
  def withTarget(thing: Thing): EventWithTarget =
    EventWithTarget(this, Some(thing.toPartial))
  def withTarget(thing: PartialThing): EventWithTarget =
    EventWithTarget(this, Some(thing))
}

case class EventWithTarget(
  event: Event,
  target: Option[PartialThing])

class Events @Inject()(
  val driver: JdbcProfile,
  val users: Provider[Users]) {
  import driver.api._

  class EventsTable(tag: Tag) extends Table[Event](tag, "events") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def `type` = column[String]("type")
    def targetEntityType = column[String]("target_entity_type")
    def targetEntityId = column[String]("target_entity_id")
    def details = column[Option[String]]("details")
    def userId = column[Int]("user_id")
    def timestamp = column[java.sql.Timestamp]("timestamp")

    def user = foreignKey("events_user_fk", userId, users.get().query)(_.id)

    override def * =
      (
        id.?,
        `type`,
        targetEntityType,
        targetEntityId,
        details,
        userId,
        timestamp
      ) <> (Event.tupled, Event.unapply)
  }

  val query = TableQuery[EventsTable]
}
