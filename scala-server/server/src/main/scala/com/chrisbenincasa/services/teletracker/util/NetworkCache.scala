package com.chrisbenincasa.services.teletracker.util

import com.chrisbenincasa.services.teletracker.db.NetworksDbAccess
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.util.Implicits._
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
      val sFut = networksDbAccess.findAllNetworks().map(_.map {
        case (ref, net) => (ref.externalSource -> ref.externalId) -> net
      }.toMap)

      sFut.onComplete(p.update(_))

      p
    }
  }
}