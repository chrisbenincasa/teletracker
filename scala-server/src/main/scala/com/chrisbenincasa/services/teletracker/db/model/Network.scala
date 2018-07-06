package com.chrisbenincasa.services.teletracker.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class Network(
  id: Option[Int],
  name: String,
  slug: String,
  shortname: String,
  homepage: Option[String],
  origin: Option[String]
)

class Networks @Inject()(
  val driver: JdbcProfile
) {
  import driver.api._

  class NetworksTable(tag: Tag) extends Table[Network](tag, "networks") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def slug = column[String]("slug", O.Unique)
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
