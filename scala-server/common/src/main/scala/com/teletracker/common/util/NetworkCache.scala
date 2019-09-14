package com.teletracker.common.util

import com.teletracker.common.db.access.NetworksDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.util.Implicits._
import com.twitter.cache.ConcurrentMapCache
import com.twitter.util.{Future => TFuture}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

object NetworkCache {
  type NetworkMap = Map[(ExternalSource, String), Network]

  final private val cache = new ConcurrentMapCache[String, NetworkMap](
    new ConcurrentHashMap[String, TFuture[NetworkMap]]()
  )
}

@Singleton
class NetworkCache @Inject()(
  networksDbAccess: NetworksDbAccess
)(implicit executionContext: ExecutionContext) {

  import NetworkCache._

  def get(): Future[NetworkMap] = {
    cache.getOrElseUpdate("NETWORKS") {
      val p = com.twitter.util.Promise[NetworkMap]()
      val sFut = networksDbAccess
        .findAllNetworks()
        .map(_.map {
          case (ref, net) => (ref.externalSource -> ref.externalId) -> net
        }.toMap)

      sFut.onComplete(p.update(_))

      p
    }
  }
}
