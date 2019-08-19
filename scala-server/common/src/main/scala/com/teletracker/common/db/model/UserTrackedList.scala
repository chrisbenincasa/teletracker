package com.teletracker.common.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class UserTrackedList(
  userId: String,
  listId: Int)

class UserTrackedLists @Inject()(
  val driver: JdbcProfile,
  val trackedLists: TrackedLists) {
  import driver.api._

  class UserTrackedListsTable(tag: Tag)
      extends Table[UserTrackedList](tag, "user_tracked_lists") {
    def userId = column[String]("user_id")
    def listId = column[Int]("list_id")

    def list =
      foreignKey("user_lists_list_id_fk", listId, trackedLists.query)(_.id)

    override def * =
      (
        userId,
        listId
      ) <> (UserTrackedList.tupled, UserTrackedList.unapply)
  }

  val query = TableQuery[UserTrackedListsTable]
}
