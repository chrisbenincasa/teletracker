package com.chrisbenincasa.services.teletracker.db.model

import com.chrisbenincasa.services.teletracker.db.CustomPostgresProfile
import com.chrisbenincasa.services.teletracker.inject.DbImplicits
import com.google.inject.Provider
import javax.inject.Inject

case class PersonThing(
  personId: Int,
  thingId: Int,
  relationType: String
)

class PersonThings @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits,
  val things: Provider[Things]
) {
  import profile.api._

  class PersonThingsTable(tag: Tag) extends Table[PersonThing](tag, "person_things") {
    def personId = column[Int]("person_id")
    def thingId = column[Int]("thing_id")
    def relationType = column[String]("relation_type")

    def personThingPrimary = primaryKey("person_thing_prim_key", (personId, thingId))
    def person = foreignKey("person_id_foreign", personId, things.get().query)(_.id)
    def thing = foreignKey("thing_id_foreign", thingId, things.get().query)(_.id)

    override def * =
      (
        personId,
        thingId,
        relationType
      ) <> (PersonThing.tupled, PersonThing.unapply)
  }

  val query = TableQuery[PersonThingsTable]
}
