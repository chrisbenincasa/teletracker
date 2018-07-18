package com.chrisbenincasa.services.teletracker

import com.chrisbenincasa.services.teletracker.controllers._
import com.chrisbenincasa.services.teletracker.exception_mappers.PassThroughExceptionMapper
import com.chrisbenincasa.services.teletracker.inject.Modules
import com.chrisbenincasa.services.teletracker.tools._
import com.chrisbenincasa.services.teletracker.util.json.JsonModule
import com.google.inject.Module
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.inject.Logging
import com.twitter.util.Await
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global

object TeletrackerServerMain extends TeletrackerServer

class TeletrackerServer(
  override protected val modules: Seq[Module] = Modules()
) extends HttpServer with Logging  {
  override protected def defaultFinatraHttpPort: String = ":3000"

  override protected def jacksonModule: Module = new JsonModule

  override protected def configureHttp(router: HttpRouter): Unit = {
    router.
      filter[LoggingMDCFilter[Request, Response]].
      filter[TraceIdMDCFilter[Request, Response]].
      exceptionMapper[PassThroughExceptionMapper].
      add[AuthController].
      add[UserController].
      add[SearchController].
      add[PeopleController].
      add[TvShowController].
      add[MetadataController]
  }
}

object Teletracker extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val cmd = args.headOption.getOrElse("server")
    val rest = if (args.nonEmpty) args.tail else Array.empty[String]

    cmd match {
      case "server" => new TeletrackerServer(modules).main(rest)
      case "reset-db" =>

        val location = new File(s"${System.getProperty("java.io.tmpdir")}/db/migrate/postgres")
        val gddl = new GenerateDdls()
        gddl.main(Array(new File(s"${location.getAbsolutePath}/V1__create.sql").getAbsolutePath))
        Await.result(gddl)

        val clean = new RunDatabaseMigration()
        clean.main(Array("-action=clean"))
        Await.result(clean)

        val migrate = new RunDatabaseMigration()
        migrate.main(Array("-action=migrate", s"-loc=$location"))
        Await.result(migrate)

        val runSeeds = new RunAllSeeds()
        runSeeds.main(rest)
        Await.result(runSeeds)

        close()
      case "generate-ddl" => new GenerateDdls().main(rest)
      case "db-migrate" => new RunDatabaseMigration().main(rest)
      case "import-movies" => ImportMovies.main(rest)
      case "import-tv" => ImportTv.main(rest)
      case "import-people" => ImportPeople.main(rest)
      case "run-all-seeds" => new RunAllSeeds().main(rest)
      case "seed-certifications" => SeedCertifications.main(rest)
      case "seed-genres" => SeedGenres.main(rest)
      case "seed-networks" => SeedNetworks.main(rest)
      case x =>
        Console.err.println(s"Unrecognized program: $x")
        sys.exit(1)
    }
  }
}