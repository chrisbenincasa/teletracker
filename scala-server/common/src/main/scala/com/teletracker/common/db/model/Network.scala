package com.teletracker.common.db.model

import com.teletracker.common.inject.DbImplicits
import com.teletracker.common.util.Slug
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class Network(
  id: Option[Int],
  name: String,
  slug: Slug,
  shortname: String,
  homepage: Option[String],
  origin: Option[String])

class Networks @Inject()(
  val driver: JdbcProfile,
  dbImplicits: DbImplicits) {
  import driver.api._
  import dbImplicits._

  class NetworksTable(tag: Tag) extends Table[Network](tag, "networks") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def slug = column[Slug]("slug", O.Unique)
    def shortname = column[String]("shortname")
    def homepage = column[Option[String]]("homepage")
    def origin = column[Option[String]]("origin")

    override def * =
      (
        id.?,
        name,
        slug,
        shortname,
        homepage,
        origin
      ) <> (Network.tupled, Network.unapply)
  }

  val query = TableQuery[NetworksTable]
}
