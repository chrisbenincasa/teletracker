package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import javax.inject.Inject
import java.time.Instant

case class UserRow(
  id: Option[Int],
  name: String,
  username: String,
  email: String,
  password: String,
  createdAt: Instant,
  lastUpdatedAt: Instant,
  preferences: Option[UserPreferences]) {
  def toFull: User = User.fromRow(this)
}

object UserPreferences {
  val default = UserPreferences(
    presentationTypes = PresentationType.values().toSet,
    showOnlyNetworkSubscriptions = Some(false),
    hideAdultContent = Some(true)
  )
}

case class UserPreferences(
  presentationTypes: Set[PresentationType],
  showOnlyNetworkSubscriptions: Option[Boolean] = None,
  hideAdultContent: Option[Boolean] = None)

object User {
  def fromRow(userRow: UserRow): User = {
    require(userRow.id.isDefined)
    User(
      userRow.id.get,
      userRow.name,
      userRow.username,
      userRow.email,
      userRow.createdAt,
      userRow.lastUpdatedAt,
      None,
      Nil,
      userRow.preferences.getOrElse(UserPreferences.default)
    )
  }
}

case class User(
  id: Int,
  name: String,
  username: String,
  email: String,
  createdAt: Instant,
  lastUpdatedAt: Instant,
  // Joins
  lists: Option[List[TrackedList]] = None,
  networkSubscriptions: List[Network] = Nil,
  userPreferences: UserPreferences = UserPreferences.default) {
  def toRow: UserRow = {
    UserRow(
      Some(id),
      name,
      username,
      email,
      "???",
      createdAt,
      lastUpdatedAt,
      Some(userPreferences)
    )
  }

  def withNetworksSubscriptions(networks: List[Network]): User = {
    this.copy(networkSubscriptions = networks)
  }

  def withLists(lists: List[TrackedList]): User = {
    withLists(Some(lists))
  }

  def withLists(lists: Option[List[TrackedList]]): User = {
    this.copy(lists = lists)
  }

  def appendLists(lists: List[TrackedList]): User = {
    withLists(this.lists.getOrElse(Nil) ++ lists)
  }
}

class Users @Inject()(
  val driver: CustomPostgresProfile,
  dbImplicits: DbImplicits) {
  import driver.api._
  import dbImplicits._

  class UsersTable(tag: Tag) extends Table[UserRow](tag, "users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def username = column[String]("username", O.Unique)
    def email = column[String]("email", O.Unique)
    def password = column[String]("password")
    def createdAt = column[Instant]("created_at")
    def lastUpdatedAt = column[Instant]("last_updated_at")
    def preferences = column[Option[UserPreferences]]("preferences")

    override def * =
      (
        id.?,
        name,
        username,
        email,
        password,
        createdAt,
        lastUpdatedAt,
        preferences
      ) <> (UserRow.tupled, UserRow.unapply)
  }

  val query = TableQuery[UsersTable]
}
