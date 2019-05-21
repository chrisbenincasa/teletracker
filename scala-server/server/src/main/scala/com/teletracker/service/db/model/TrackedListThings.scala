package com.teletracker.service.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class TrackedListThing(
  listId: Int,
  thingId: Int)

class TrackedListThings @Inject()(
  val driver: JdbcProfile,
  val trackedLists: TrackedLists,
  val things: Things) {
  import driver.api._

  class TrackedListThingsTable(tag: Tag)
      extends Table[TrackedListThing](tag, "list_things") {
    def listId = column[Int]("lists_id")
    def thingId = column[Int]("objects_id")

    def primKey = primaryKey("list_id_thing_id", (listId, thingId))

    def list_fk =
      foreignKey("list_things_list_fk", listId, trackedLists.query)(_.id)
    def thing_fk =
      foreignKey("list_things_thing_fk", thingId, things.query)(_.id)

    override def * =
      (listId, thingId) <> (TrackedListThing.tupled, TrackedListThing.unapply)
  }

  val query = TableQuery[TrackedListThingsTable]
}
