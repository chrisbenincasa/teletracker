package com.teletracker.common.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile
import java.util.UUID

case class ThingGenre(
  thingId: UUID,
  genreId: Int)

class ThingGenres @Inject()(
  val driver: JdbcProfile,
  val things: Things,
  val genres: Genres) {

  import driver.api._

  class ThingGenresTable(tag: Tag)
      extends Table[ThingGenre](tag, "thing_genres") {
    def thingId = column[UUID]("thing_id")
    def genreId = column[Int]("genre_id")

    def pk = primaryKey("thing_genres_pk_thing_network", (thingId, genreId))

    def thing =
      foreignKey("thing_genres_fk_things", thingId, things.query)(_.id)
    def genre = foreignKey("thing_genres_fk_genre", genreId, genres.query)(_.id)

    override def * =
      (
        thingId,
        genreId
      ) <> (ThingGenre.tupled, ThingGenre.unapply)
  }

  val query = TableQuery[ThingGenresTable]
}