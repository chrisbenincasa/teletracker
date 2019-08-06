package com.teletracker.common.db.model

import com.teletracker.common.inject.DbImplicits
import com.teletracker.common.util.Slug
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class Genre(
  id: Option[Int],
  name: String,
  `type`: GenreType,
  slug: Slug)

class Genres @Inject()(
  val driver: JdbcProfile,
  val users: Users,
  val implicits: DbImplicits) {
  import driver.api._
  import implicits._

  class GenresTable(tag: Tag) extends Table[Genre](tag, "genres") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def `type` = column[GenreType]("type")
    def slug = column[Slug]("slug")

    override def * =
      (
        id.?,
        name,
        `type`,
        slug
      ) <> (Genre.tupled, Genre.unapply)
  }

  val query = TableQuery[GenresTable]
}
