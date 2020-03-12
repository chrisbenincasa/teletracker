package com.teletracker.tasks.db

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.db.legacy_model.Network
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import scala.concurrent.ExecutionContext

class MigrateNetworksToDynamo @Inject()(
  metadataDbAccess: MetadataDbAccess,
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val networkId = args.value[Int]("networkId")
    val input = args.valueOrThrow[URI]("input")
    val dryRun = args.valueOrDefault("dryRun", true)

    sourceRetriever
      .getSource(input)
      .getLines()
      .map(Network.fromLine(_))
      .filter(network => networkId.forall(_ == network.id))
      .foreach(network => {
        val storedNetwork = StoredNetwork(
          id = network.id,
          name = network.name,
          slug = network.slug,
          shortname = network.shortname,
          homepage = network.homepage,
          origin = network.origin
        )

        if (dryRun) {
          println(s"Would've inserted ${storedNetwork}")
        } else {
          metadataDbAccess.saveNetwork(storedNetwork).await()
        }
      })
  }
}
