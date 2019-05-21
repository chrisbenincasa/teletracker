package com.teletracker.service.util

import com.teletracker.service.db.NetworksDbAccess
import com.teletracker.service.db.model._
import com.teletracker.service.util.Implicits._
import com.twitter.cache.ConcurrentMapCache
import com.twitter.util.{Future => TFuture}
import java.util.concurrent.ConcurrentHashMap
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NetworkCache @Inject()(
  networksDbAccess: NetworksDbAccess
)(implicit executionContext: ExecutionContext) {
  type NetworkMap = Map[(ExternalSource, String), Network]
  private val cache = new ConcurrentMapCache[String, NetworkMap](
    new ConcurrentHashMap[String, TFuture[NetworkMap]]()
  )

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
