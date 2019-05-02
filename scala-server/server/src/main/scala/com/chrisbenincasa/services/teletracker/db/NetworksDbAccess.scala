package com.chrisbenincasa.services.teletracker.db

import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.{DbImplicits, DbProvider}
import com.chrisbenincasa.services.teletracker.util.Slug
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class NetworksDbAccess @Inject()(
  val provider: DbProvider,
  val networks: Networks,
  val networkReferences: NetworkReferences,
  val thingNetworks: ThingNetworks,
  dbImplicits: DbImplicits
)(implicit executionContext: ExecutionContext) extends DbAccess {
  import provider.driver.api._
  import dbImplicits._

  def findNetworksBySlugs(slugs: Set[Slug]) = {
    if (slugs.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        networks.query.filter(_.slug inSetBind slugs).result
      }
    }
  }

  def findAllNetworks() = {
    run {
      networkReferences.query.flatMap(ref => ref.networkId_fk.map(ref -> _)).result
    }
  }

  def saveNetwork(network: Network) = {
    run {
      (networks.query returning networks.query.map(_.id) into ((n, id) => n.copy(id = Some(id)))) += network
    }
  }

  def saveNetworkReference(networkReference: NetworkReference) = {
    run {
      networkReferences.query += networkReference
    }
  }

  def saveNetworkAssociation(thingNetwork: ThingNetwork) = {
    run {
      thingNetworks.query.insertOrUpdate(thingNetwork)
    }
  }
}
