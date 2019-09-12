package com.teletracker.common.db.model

import com.teletracker.common.db.{CustomPostgresProfile, DbImplicits}
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.time.Instant

object UserPreferences {
  val default = UserPreferences(
    presentationTypes = PresentationType.values().toSet,
    showOnlyNetworkSubscriptions = Some(false),
    hideAdultContent = Some(true)
  )
}

@JsonCodec
case class UserPreferences(
  presentationTypes: Set[PresentationType],
  showOnlyNetworkSubscriptions: Option[Boolean] = None,
  hideAdultContent: Option[Boolean] = None)

case class UserMetadataRow(
  userId: String,
  preferences: Option[UserPreferences],
  createdAt: Instant,
  lastUpdatedAt: Instant)

class UsersMetadata @Inject()(
  val driver: CustomPostgresProfile,
  dbImplicits: DbImplicits) {
  import driver.api._
  import dbImplicits._

  class UsersMetadataTable(tag: Tag)
      extends Table[UserMetadataRow](tag, "users_metadata") {
    def userId = column[String]("user_id", O.PrimaryKey)
    def createdAt = column[Instant]("created_at")
    def lastUpdatedAt = column[Instant]("last_updated_at")
    def preferences = column[Option[UserPreferences]]("preferences")

    override def * =
      (
        userId,
        preferences,
        createdAt,
        lastUpdatedAt
      ) <> (UserMetadataRow.tupled, UserMetadataRow.unapply)
  }

  val query = TableQuery[UsersMetadataTable]
}
