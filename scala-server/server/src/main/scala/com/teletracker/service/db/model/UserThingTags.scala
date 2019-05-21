package com.teletracker.service.db.model

import com.teletracker.service.db.CustomPostgresProfile
import com.teletracker.service.inject.DbImplicits
import com.google.inject.Provider
import javax.inject.Inject

case class UserThingTag(
  id: Int,
  userId: Int,
  thingId: Int,
  action: UserThingTagType,
  value: Option[Double])

class UserThingTags @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits,
  val users: Provider[Users],
  val things: Provider[Things]) {
  import profile.api._
  import dbImplicits._

  class UserThingTagsTable(tag: Tag)
      extends Table[UserThingTag](tag, "user_thing_tags") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("user_id")
    def thingId = column[Int]("thing_id")
    def action = column[UserThingTagType]("action")
    def value = column[Option[Double]]("value")

    def user = foreignKey("user_id_foreign", userId, users.get().query)(_.id)
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
