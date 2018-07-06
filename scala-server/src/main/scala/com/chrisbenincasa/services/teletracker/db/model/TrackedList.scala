package com.chrisbenincasa.services.teletracker.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class TrackedListRow(
  id: Option[Int],
  name: String,
  isDefault: Boolean,
  userId: Int
) {
  def toFull: TrackedList = TrackedList.fromRow(this)
}

object TrackedList {
  def fromRow(row: TrackedListRow): TrackedList = {
    require(row.id.isDefined)
    TrackedList(row.id.get, row.name, row.isDefault, row.userId)
  }
}

case class TrackedList(
  id: Int,
  name: String,
  isDefault: Boolean,
  userId: Int,

  things: Option[List[Thing]] = None
) {
  def withThings(things: List[Thing]): TrackedList = {
    this.copy(things = Some(things))
  }
}

class TrackedLists @Inject()(
  val driver: JdbcProfile,
  val users: Users
) {
  import driver.api._

  class TrackedListsTable(tag: Tag) extends Table[TrackedListRow](tag, "lists") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def isDefault = column[Boolean]("is_default", O.Default(false))
    def createdAt = column[java.sql.Timestamp]("created_at")
    def lastUpdatedAt = column[java.sql.Timestamp]("last_updated_at")
    def userId = column[Int]("user_id")

    def userId_fk = foreignKey("lists_user_id_fk", userId, users.query)(_.id)

    override def * =
      (
        id.?,
        name,
        isDefault,
        userId
      ) <> (TrackedListRow.tupled, TrackedListRow.unapply)
  }

  val query = TableQuery[TrackedListsTable]
}
