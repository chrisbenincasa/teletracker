package com.teletracker.common.db.model

import com.teletracker.common.db.{CustomPostgresProfile, DbImplicits}
import com.teletracker.common.util.Slug
import io.circe._
import javax.inject.Inject
import java.time.OffsetDateTime
import java.util.UUID

class People @Inject()(
  val profile: CustomPostgresProfile,
  dbImplicits: DbImplicits) {
  import profile.api._
  import dbImplicits._

  class PeopleTable(tag: Tag) extends Table[Person](tag, "people") {
    def id = column[UUID]("id", O.PrimaryKey)
    def name = column[String]("name")
    def normalizedName = column[Slug]("normalized_name")
    def createdAt =
      column[OffsetDateTime](
        "created_at",
        O.SqlType("timestamp with time zone")
      )
    def lastUpdatedAt =
      column[OffsetDateTime](
        "last_updated_at",
        O.SqlType("timestamp with time zone")
      )
    def metadata = column[Option[Json]]("metadata", O.SqlType("jsonb"))

    def tmdbId = column[Option[String]]("tmdb_id")

    def popularity = column[Option[Double]]("popularity")

    def projWithMetadata(includeMetadata: Boolean) =
      (
        id,
        name,
        normalizedName,
        createdAt,
        lastUpdatedAt,
        if (includeMetadata) metadata else Rep.None[Json],
        tmdbId,
        popularity
      ) <> (Person.tupled, Person.unapply)

    override def * =
      projWithMetadata(true)
  }

  val query = TableQuery[PeopleTable]
}
