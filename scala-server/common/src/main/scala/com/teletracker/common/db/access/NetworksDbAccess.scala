package com.teletracker.common.db.access

import com.google.inject.assistedinject.Assisted
import com.teletracker.common.db.{
  AsyncDbProvider,
  BaseDbProvider,
  DbImplicits,
  DbMonitoring,
  SyncDbProvider
}
import com.teletracker.common.db.model._
import com.teletracker.common.util.{GeneralizedDbFactory, Slug}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class NetworksDbAccess @Inject()(
  val provider: BaseDbProvider,
  val networks: Networks,
  val networkReferences: NetworkReferences,
  val thingNetworks: ThingNetworks,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends AbstractDbAccess(dbMonitoring) {
  import dbImplicits._
  import provider.driver.api._

  def findNetworksBySlugs(slugs: Set[Slug]) = {
    if (slugs.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      run {
        networks.query.filter(_.slug inSetBind slugs).result
      }
    }
  }

  def findAllNetworks(): Future[Seq[(NetworkReference, Network)]] = {
    run {
      networkReferences.query
        .flatMap(ref => ref.networkId_fk.map(ref -> _))
        .result
    }
  }

  def saveNetwork(network: Network) = {
    run {
      (networks.query returning networks.query.map(_.id) into (
        (
          n,
          id
        ) => n.copy(id = Some(id))
      )) += network
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
