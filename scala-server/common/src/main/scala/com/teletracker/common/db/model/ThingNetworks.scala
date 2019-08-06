package com.teletracker.common.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile
import java.util.UUID

case class ThingNetwork(
  thingId: UUID,
  networkId: Int)

class ThingNetworks @Inject()(
  val driver: JdbcProfile,
  val things: Things,
  val networks: Networks) {

  import driver.api._

  class ThingNetworksTable(tag: Tag)
      extends Table[ThingNetwork](tag, "thing_networks") {
    def thingId = column[UUID]("objects_id")
    def networkId = column[Int]("networks_id")

    def pk = primaryKey("thing_networks_pk_thing_network", (thingId, networkId))

    def fkThing =
      foreignKey("thing_networks_fk_things", thingId, things.query)(_.id)
    def fkNetwork =
      foreignKey("thing_networks_fk_networks", networkId, networks.query)(_.id)

    override def * =
      (
        thingId,
        networkId
      ) <> (ThingNetwork.tupled, ThingNetwork.unapply)
  }

  val query = TableQuery[ThingNetworksTable]
}
