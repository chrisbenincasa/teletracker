package com.chrisbenincasa.services.teletracker.tools

import com.chrisbenincasa.services.teletracker.db.NetworksDbAccess
import com.chrisbenincasa.services.teletracker.db.model._
import com.chrisbenincasa.services.teletracker.inject.{DbProvider, Modules}
import com.chrisbenincasa.services.teletracker.model.justwatch.Provider
import com.chrisbenincasa.services.teletracker.util.Slug
import com.google.inject.Module
import com.twitter.inject.app.App
import java.io.File
import javax.inject.Inject
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object SeedNetworks extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    injector.instance[NetworkSeeder].run()
  }
}

class NetworkSeeder @Inject()(
  dbProvider: DbProvider,
  networks: Networks,
  networkReferences: NetworkReferences,
  networksDbAccess: NetworksDbAccess
) {
  def run(): Unit = {
    import io.circe.generic.auto._
    import io.circe.parser._
    import networks.driver.api._

    Await.result(dbProvider.getDB.run(networkReferences.query.delete), Duration.Inf)
    Await.result(dbProvider.getDB.run(networks.query.delete), Duration.Inf)

    val lines = scala.io.Source.fromFile(new File(System.getProperty("user.dir") + "/data/providers.json")).getLines().mkString("").trim

    parse(lines).flatMap(_.as[List[Provider]]) match {
      case Left(err) =>
        println(err)
        sys.exit(1)
      case Right(providers) =>
        val inserts = providers.map(provider => {
          val network = Network(None, provider.clear_name, Slug(provider.clear_name), provider.short_name, None, None)

          val networkInsert = networksDbAccess.saveNetwork(network)
          networkInsert.flatMap(n => {
            networksDbAccess.saveNetworkReference(NetworkReference(None, ExternalSource.JustWatch, provider.id.toString, n.id.get))
          })
        })

        Await.result(Future.sequence(inserts), Duration.Inf)
    }
  }
}
