package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import com.google.inject.Provider
import javax.inject.Inject
import java.util.UUID

case class PersonThing(
  personId: UUID,
  thingId: UUID,
  relationType: PersonAssociationType,
  characterName: Option[String],
  order: Option[Int])

class PersonThings @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits,
  val things: Provider[Things],
  val people: Provider[People]) {
  import profile.api._
  import dbImplicits._

  class PersonThingsTable(tag: Tag)
      extends Table[PersonThing](tag, "person_things") {
    def personId = column[UUID]("person_id")
    def thingId = column[UUID]("thing_id")
    def relationType = column[PersonAssociationType]("relation_type")
    def characterName = column[Option[String]]("character_name")
    def order = column[Option[Int]]("ordering")

    def personThingPrimary =
      primaryKey("person_thing_prim_key", (personId, thingId))
    def person =
      foreignKey("person_id_foreign", personId, people.get().query)(_.id)
    def thing =
      foreignKey("thing_id_foreign", thingId, things.get().rawQuery)(_.id)

    override def * =
      (
        personId,
        thingId,
        relationType,
        characterName,
        order
      ) <> (PersonThing.tupled, PersonThing.unapply)
  }

  val query = TableQuery[PersonThingsTable]
}