package com.chrisbenincasa.services.teletracker.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class UserRow(
  id: Option[Int],
  name: String,
  username: String,
  email: String,
  password: String,
  createdAt: java.sql.Timestamp,
  lastUpdatedAt: java.sql.Timestamp
) {
  def toFull: User = User.fromRow(this)
}

case class UserNetworkPreference(
  id: Int,
  userId: Int,
  networkId: Int
)

object User {
  def fromRow(userRow: UserRow): User = {
    require(userRow.id.isDefined)
    User(userRow.id.get, userRow.name, userRow.username, userRow.email, userRow.createdAt, userRow.lastUpdatedAt)
  }
}

case class User(
  id: Int,
  name: String,
  username: String,
  email: String,
  createdAt: java.sql.Timestamp,
  lastUpdatedAt: java.sql.Timestamp,
  lists: Option[List[TrackedList]] = None
) {
  def withLists(lists: List[TrackedList]): User = {
    this.copy(lists = Some(lists))
  }

  def withLists(lists: Option[List[TrackedList]]): User = {
    this.copy(lists = lists)
  }
}

class Users @Inject()(
  val driver: JdbcProfile
) {
  import driver.api._

  class UsersTable(tag: Tag) extends Table[UserRow](tag, "users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def username = column[String]("username", O.Unique)
    def email = column[String]("email", O.Unique)
    def password = column[String]("password")
    def createdAt = column[java.sql.Timestamp]("created_at")
    def lastUpdatedAt = column[java.sql.Timestamp]("last_updated_at")

    override def * =
      (
        id.?,
        name,
        username,
        email,
        password,
        createdAt,
        lastUpdatedAt
      ) <> (UserRow.tupled, UserRow.unapply)
  }

  val query = TableQuery[UsersTable]
}
