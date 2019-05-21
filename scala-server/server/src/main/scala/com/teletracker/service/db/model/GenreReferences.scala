package com.teletracker.service.db.model

import com.teletracker.service.inject.DbImplicits
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class GenreReference(
  id: Option[Int],
  externalSource: ExternalSource,
  externalId: String,
  genreId: Int)

class GenreReferences @Inject()(
  val driver: JdbcProfile,
  val genres: Genres,
  val implicits: DbImplicits) {
  import driver.api._
  import implicits._

  class GenreReferencesTable(tag: Tag)
      extends Table[GenreReference](tag, "genre_references") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def externalSource = column[ExternalSource]("external_source")
    def externalId = column[String]("external_id")
    def genreId = column[Int]("genre_id")

    def source_id_genre_unique =
      index(
        "genre_references_source_id_genre_unique",
        (externalSource, externalId, genreId),
        true
      )

    def genre =
      foreignKey("network_references_network_id_fk", genreId, genres.query)(
        _.id
      )

    override def * =
      (
        id.?,
        externalSource,
        externalId,
        genreId
      ) <> (GenreReference.tupled, GenreReference.unapply)
  }

  val query = TableQuery[GenreReferencesTable]
}
