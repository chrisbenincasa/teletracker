package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import com.google.inject.Provider
import javax.inject.Inject
import java.util.UUID

case class UserThingTag(
  id: Int,
  userId: String,
  thingId: UUID,
  action: UserThingTagType,
  value: Option[Double])

class UserThingTags @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits,
  val things: Provider[Things]) {
  import profile.api._
  import dbImplicits._

  class UserThingTagsTable(tag: Tag)
      extends Table[UserThingTag](tag, "user_thing_tags") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[String]("user_id")
    def thingId = column[UUID]("thing_id")
    def action = column[UserThingTagType]("action")
    def value = column[Option[Double]]("value")

    def thing =
      foreignKey("thing_id_foreign", thingId, things.get().query)(_.id)

    override def * =
      (
        id,
        userId,
        thingId,
        action,
        value
      ) <> (UserThingTag.tupled, UserThingTag.unapply)
  }

  val query = TableQuery[UserThingTagsTable]
}
