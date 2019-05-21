package com.teletracker.service.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class UserCredential(
  userId: Int,
  password: String,
  salt: Array[Byte])

class UserCredentials @Inject()(
  val driver: JdbcProfile,
  val users: Users) {

  import driver.api._

  type TableType = UserCredentialsTable

  class UserCredentialsTable(tag: Tag)
      extends Table[UserCredential](tag, "user_credentials") {
    def userId = column[Int]("user_id")
    def password = column[String]("password")
    def salt = column[Array[Byte]]("salt")
    def * =
      (userId, password, salt) <> (UserCredential.tupled, UserCredential.unapply)

    def user =
      foreignKey("user_credentials_user_id", userId, users.query)(
        _.id,
        onUpdate = ForeignKeyAction.Restrict,
        onDelete = ForeignKeyAction.Cascade
      )
  }

  val query = TableQuery[UserCredentialsTable]
}
