package com.teletracker.common.db.model

import com.teletracker.common.inject.DbImplicits
import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class NetworkReference(
  id: Option[Int],
  externalSource: ExternalSource,
  externalId: String,
  networkId: Int)

class NetworkReferences @Inject()(
  val driver: JdbcProfile,
  val implicits: DbImplicits,
  val networks: Networks) {
  import driver.api._
  import implicits._

  class NetworkReferencesTable(tag: Tag)
      extends Table[NetworkReference](tag, "network_references") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def externalSource = column[ExternalSource]("external_source")
    def externalId = column[String]("external_id")
    def networkId = column[Int]("network_id")

    def source_id_network_unique =
      index(
        "network_references_source_id_network_unique",
        (externalSource, externalId, networkId),
        true
      )

    def networkId_fk =
      foreignKey("network_references_network_id_fk", networkId, networks.query)(
        _.id
      )

    override def * =
      (
        id.?,
        externalSource,
        externalId,
        networkId
      ) <> (NetworkReference.tupled, NetworkReference.unapply)
  }

  val query = TableQuery[NetworkReferencesTable]
}
