package com.teletracker.common.db.model

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.util.NetworkCache
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

// Backcompat for supported networks to DB networks
object SupportedNetworkLookup {
  final val SpecialCaseMap =
    Map(
      "apple-itunes" -> SupportedNetwork.AppleTv,
      "hbo-go" -> SupportedNetwork.Hbo,
      "hbo-now" -> SupportedNetwork.Hbo
    )

  def resolveSupportedNetwork(s: String): Option[SupportedNetwork] = {
    val normal = s.toLowerCase()
    Try(SupportedNetwork.fromString(normal)).toOption
      .orElse(SpecialCaseMap.get(normal))
  }
}

class SupportedNetworkLookup @Inject()(
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext) {
  import SupportedNetworkLookup._

  def resolveSupportedNetworks(
    networks: Set[String]
  ): Future[Set[StoredNetwork]] = {
    val supportedNetworks = networks.flatMap(resolveSupportedNetwork)
    networkCache
      .getAllNetworks()
      .map(cachedNetworks => {
        cachedNetworks
          .filter(
            network =>
              network.supportedNetwork.exists(supportedNetworks.contains)
          )
          .toSet
      })
  }
}
