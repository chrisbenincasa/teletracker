package com.teletracker.service.db.model

import javax.inject.{Inject, Provider}
import slick.jdbc.JdbcProfile
import java.util.UUID

case class Collection(
  id: Int,
  name: String,
  overview: Option[String],
  tmdb_id: Option[String])

case class CollectionThing(
  id: Int,
  collectionId: Int,
  thingId: UUID)

class Collections @Inject()(
  val driver: JdbcProfile,
  val things: Provider[Things]) {
  import driver.api._

  class CollectionsTable(tag: Tag)
      extends Table[Collection](tag, "collections") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def overview = column[Option[String]]("overview", O.SqlType("text"))
    def tmdbId = column[Option[String]]("tmdb_id")

    override def * =
      (
        id,
        name,
        overview,
        tmdbId
      ) <> (Collection.tupled, Collection.unapply)
  }

  val collectionsQuery = TableQuery[CollectionsTable]

  class CollectionThingsTable(tag: Tag)
      extends Table[CollectionThing](tag, "collection_things") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def collectionId = column[Int]("id")
    def thingId = column[UUID]("id")

    def collection =
      foreignKey(
        "collection_things_collection_fk",
        collectionId,
        collectionsQuery
      )(_.id, ForeignKeyAction.NoAction, ForeignKeyAction.Restrict)

    def thing =
      foreignKey("collection_things_thing_fk", thingId, things.get().query)(
        _.id
      )

    override def * =
      (
        id,
        collectionId,
        thingId
      ) <> (CollectionThing.tupled, CollectionThing.unapply)
  }
}
