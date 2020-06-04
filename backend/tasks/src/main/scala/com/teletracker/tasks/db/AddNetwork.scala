package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.{
  StoredNetwork,
  StoredNetworkReference
}
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Slug
import javax.inject.Inject

class AddNetwork @Inject()(metadataDbAccess: MetadataDbAccess)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val name = rawArgs.valueOrThrow[String]("name").replaceAll("_", " ")
    val shortname = rawArgs.valueOrThrow[String]("shortname")
    val dryRun = rawArgs.valueOrDefault("dryRun", true)

    val allNetworks = metadataDbAccess.getAllNetworks().await()

    val maxId = allNetworks.maxBy(_.id).id

    val network = StoredNetwork(
      id = maxId + 1,
      name = name,
      slug = Slug.forString(name),
      shortname = shortname,
      homepage = None,
      origin = None
    )

    if (dryRun) {
      logger.info(s"Would've inserted new Network: ${network}")
    } else {
      logger.info(s"Inserting new network Network: ${network}")
      val inserted = metadataDbAccess.saveNetwork(network).await()
      logger.info(s"Inserted new network with id = ${inserted.id}")
    }
  }
}

class AddNetworkReference @Inject()(metadataDbAccess: MetadataDbAccess)
    extends UntypedTeletrackerTask {

  override protected def runInternal(): Unit = {
    val networkId = rawArgs.valueOrThrow[Int]("networkId")
    val externalId = rawArgs.valueOrThrow[String]("externalId")
    val externalSource = rawArgs.valueOrThrow[ExternalSource]("externalSource")
    val dryRun = rawArgs.valueOrDefault("dryRun", true)

    val network = metadataDbAccess.getNetworkById(networkId).await()
    if (network.isEmpty) {
      throw new IllegalArgumentException(
        s"Could not find network with id = $networkId"
      )
    }

    val reference =
      StoredNetworkReference(externalSource, externalId, networkId)
    if (dryRun) {
      logger.info(s"Would've saved reference: $reference")
    } else {
      logger.info(s"Saving reference: $reference")
      metadataDbAccess.saveNetworkReference(reference).await()
    }
  }
}
