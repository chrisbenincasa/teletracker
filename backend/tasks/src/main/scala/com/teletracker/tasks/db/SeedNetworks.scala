package com.teletracker.tasks.db

import com.teletracker.common.db.dynamo.MetadataDbAccess
import com.teletracker.common.db.dynamo.model.{
  StoredNetwork,
  StoredNetworkReference
}
import com.teletracker.common.db.model._
import com.teletracker.common.model.justwatch.Provider
import com.teletracker.common.util.Slug
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class NetworkSeeder @Inject()(metadataDbAccess: MetadataDbAccess)
    extends TeletrackerTaskWithDefaultArgs {
  def runInternal(args: Args): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._

    // TODO: Delete networks in dynamo?

    val lines = scala.io.Source
      .fromFile(
        new File(System.getProperty("user.dir") + "/server/data/providers.json")
      )
      .getLines()
      .mkString("")
      .trim

    parse(lines).flatMap(_.as[List[Provider]]) match {
      case Left(err) =>
        println(err)
        sys.exit(1)
      case Right(providers) =>
        val inserts = providers.zipWithIndex.map {
          case (provider, index) =>
            val network = StoredNetwork(
              index + 1,
              provider.clear_name,
              Slug.forString(provider.clear_name),
              provider.short_name,
              None,
              None
            )

            val networkInsert = metadataDbAccess.saveNetwork(network)

            networkInsert.flatMap(n => {
              metadataDbAccess.saveNetworkReference(
                StoredNetworkReference(
                  ExternalSource.JustWatch,
                  provider.id.toString,
                  n.id
                )
              )
            })
        }

        Await.result(Future.sequence(inserts), Duration.Inf)
    }
  }
}
