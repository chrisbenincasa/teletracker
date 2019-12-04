package com.teletracker.tasks.db

import com.teletracker.common.db.access.NetworksDbAccess
import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MigrateNetworksToDynamo @Inject()(
  networksDbAccess: NetworksDbAccess,
  metadataDbAccess: MetadataDbAccess
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val networkId = args.value[Int]("networkId")

    val networksByReference = networksDbAccess.findAllNetworks().await()

    val networks = networksByReference
      .map(_._2)
      .groupBy(_.id.get)
      .values
      .flatMap(_.headOption)

    networks
      .filter(network => networkId.forall(_ == network.id.get))
      .foreach(network => {
        val storedNetwork = StoredNetwork(
          id = network.id.get,
          name = network.name,
          slug = network.slug,
          shortname = network.shortname,
          homepage = network.homepage,
          origin = network.origin
        )

        metadataDbAccess.saveNetwork(storedNetwork).await()
      })
  }
}
