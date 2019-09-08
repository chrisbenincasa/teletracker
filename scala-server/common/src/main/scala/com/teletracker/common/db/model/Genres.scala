package com.teletracker.common.db.model

import com.teletracker.common.db.CustomPostgresProfile
import com.teletracker.common.inject.DbImplicits
import com.teletracker.common.util.Slug
import javax.inject.Inject

case class Genre(
  id: Option[Int],
  name: String,
  slug: Slug,
  `type`: List[GenreType])

class Genres @Inject()(
  val driver: CustomPostgresProfile,
  val implicits: DbImplicits) {
  import driver.api._
  import implicits._

  class GenresTable(tag: Tag) extends Table[Genre](tag, "genres") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def `type` = column[List[GenreType]]("type")
    def slug = column[Slug]("slug")

    override def * =
      (
        id.?,
        name,
        slug,
        `type`
      ) <> (Genre.tupled, Genre.unapply)
  }

  val query = TableQuery[GenresTable]
}
