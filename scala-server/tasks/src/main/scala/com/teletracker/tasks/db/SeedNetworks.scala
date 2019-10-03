package com.teletracker.tasks.db

import com.teletracker.common.db.SyncDbProvider
import com.teletracker.common.db.access.NetworksDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.model.justwatch.Provider
import com.teletracker.common.util.Slug
import com.teletracker.tasks.{
  TeletrackerTask,
  TeletrackerTaskApp,
  TeletrackerTaskWithDefaultArgs
}
import javax.inject.Inject
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object SeedNetworks extends TeletrackerTaskApp {
  override protected def runInternal(): Unit = {
    injector.instance[NetworkSeeder].runInternal()
  }
}

class NetworkSeeder @Inject()(
  dbProvider: SyncDbProvider,
  networks: Networks,
  networkReferences: NetworkReferences,
  networksDbAccess: NetworksDbAccess)
    extends TeletrackerTaskWithDefaultArgs {
  def runInternal(args: Args): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._
    import networks.driver.api._

    Await.result(
      dbProvider.getDB.run(networkReferences.query.delete),
      Duration.Inf
    )
    Await.result(dbProvider.getDB.run(networks.query.delete), Duration.Inf)

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
        val inserts = providers.map(provider => {
          val network = Network(
            None,
            provider.clear_name,
            Slug.forString(provider.clear_name),
            provider.short_name,
            None,
            None
          )

          val networkInsert = networksDbAccess.saveNetwork(network)
          networkInsert.flatMap(n => {
            networksDbAccess.saveNetworkReference(
              NetworkReference(
                None,
                ExternalSource.JustWatch,
                provider.id.toString,
                n.id.get
              )
            )
          })
        })

        Await.result(Future.sequence(inserts), Duration.Inf)
    }
  }
}
