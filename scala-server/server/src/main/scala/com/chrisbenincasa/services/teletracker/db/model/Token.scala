package com.chrisbenincasa.services.teletracker.db.model

import com.chrisbenincasa.services.teletracker.db.CustomPostgresProfile
import com.chrisbenincasa.services.teletracker.inject.DbImplicits
import javax.inject.Inject
import org.joda.time.DateTime

case class TokenRow(
  id: Option[Int],
  userId: Int,
  token: String,
  createdAt: DateTime,
  lastUpdatedAt: DateTime,
  revokedAt: Option[DateTime]
)

class Tokens @Inject()(
  val driver: CustomPostgresProfile,
  val users: Users,
  dbImplicits: DbImplicits
) {
  import driver.api._

  class TokensTable(tag: Tag) extends Table[TokenRow](tag, "tokens") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("user_id")
    def token = column[String]("token", O.SqlType("text"))
    def createdAt = column[DateTime]("created_at")
    def lastUpdatedAt = column[DateTime]("last_updated_at")
    def revokedAt = column[Option[DateTime]]("revoked_at")

    def userIdx = index("user_id_idx", userId)
    def user = foreignKey("user_fk", userId, users.query)(_.id)

    override def * =
      (
        id.?,
        userId,
        token,
        createdAt,
        lastUpdatedAt,
        revokedAt
      ) <> (TokenRow.tupled, TokenRow.unapply)
  }

  val query = TableQuery[TokensTable]
}

