package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import javax.inject.Inject
import org.joda.time.DateTime
import java.time.OffsetDateTime

case class TokenRow(
  id: Option[Int],
  userId: Int,
  token: String,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  revokedAt: Option[OffsetDateTime])

class Tokens @Inject()(
  val driver: CustomPostgresProfile,
  val users: Users,
  dbImplicits: DbImplicits) {
  import driver.api._

  class TokensTable(tag: Tag) extends Table[TokenRow](tag, "tokens") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("user_id")
    def token = column[String]("token", O.SqlType("text"))
    def createdAt =
      column[OffsetDateTime]("created_at", O.SqlType("timestamp with timezone"))
    def lastUpdatedAt =
      column[OffsetDateTime](
        "last_updated_at",
        O.SqlType("timestamp with timezone")
      )
    def revokedAt =
      column[Option[OffsetDateTime]](
        "revoked_at",
        O.SqlType("timestamp with timezone")
      )

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
