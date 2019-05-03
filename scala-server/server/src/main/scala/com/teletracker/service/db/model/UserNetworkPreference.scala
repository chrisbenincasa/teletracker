package com.teletracker.service.db.model

import javax.inject.Inject
import slick.jdbc.JdbcProfile

case class UserNetworkPreference(
  id: Int,
  userId: Int,
  networkId: Int
)

class UserNetworkPreferences @Inject()(
  val driver: JdbcProfile,
  val users: Users,
  val networks: Networks
) {
  import driver.api._

  class UserNetworkPreferencesTable(tag: Tag) extends Table[UserNetworkPreference](tag, "user_network_preferences") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("user_id")
    def networkId = column[Int]("network_id")

    def users_fk = foreignKey("user_network_preferences_user_fk", userId, users.query)(_.id)
    def networks_fk = foreignKey("user_network_preferences_network_fk", networkId, networks.query)(_.id)

    override def * =
      (
        id,
        userId,
        networkId
      ) <> (UserNetworkPreference.tupled, UserNetworkPreference.unapply)
  }

  val query = TableQuery[UserNetworkPreferencesTable]
}