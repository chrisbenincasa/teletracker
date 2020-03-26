package com.teletracker.common.util

import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model._
import com.teletracker.common.util.Implicits._
import com.twitter.cache.ConcurrentMapCache
import com.twitter.util.{Future => TFuture}
import javax.inject.{Inject, Singleton}
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

object NetworkCache {
  final private val cache = new ConcurrentMapCache[String, List[StoredNetwork]](
    new ConcurrentHashMap[String, TFuture[List[StoredNetwork]]]()
  )

  final private val referenceCache =
    new ConcurrentMapCache[(ExternalSource, String), StoredNetwork](
      new ConcurrentHashMap[(ExternalSource, String), TFuture[StoredNetwork]]()
    )
}

@Singleton
class NetworkCache @Inject()(
  metadataDbAccess: MetadataDbAccess
)(implicit executionContext: ExecutionContext) {

  import NetworkCache._

  def getAllNetworks(): Future[List[StoredNetwork]] = {
    cache.getOrElseUpdate("NETWORKS") {
      val p = com.twitter.util.Promise[List[StoredNetwork]]()
      val sFut = metadataDbAccess.getAllNetworks()

      sFut.onComplete(p.update(_))

      p
    }
  }

  def getByReference(
    externalSource: ExternalSource,
    externalId: String
  ): Future[StoredNetwork] = {
    referenceCache.getOrElseUpdate(externalSource -> externalId) {
      val p = com.twitter.util.Promise[StoredNetwork]()
      val sFut =
        metadataDbAccess.getNetworkByReference(externalSource, externalId)

      sFut.foreach {
        case None =>
          throw new IllegalArgumentException(
            s"No network for external $externalSource, $externalId"
          )
        case Some((_, network)) => p.setValue(network)
      }

      p
    }
  }
}
