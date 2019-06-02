package com.teletracker.service

import com.google.inject.Module
import com.teletracker.service.controllers._
import com.teletracker.service.exception_mappers.PassThroughExceptionMapper
import com.teletracker.service.inject.Modules
import com.teletracker.service.process.tmdb.TmdbBackgroundProcessor
import com.teletracker.service.tools._
import com.teletracker.service.util.json.JsonModule
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.inject.Logging
import com.twitter.util.Await
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global

object TeletrackerServerMain extends TeletrackerServer

class TeletrackerServer(override protected val modules: Seq[Module] = Modules())
    extends HttpServer
    with Logging {

  override protected def defaultHttpPort: String = ":3001"

  override protected def jacksonModule: Module = new JsonModule

  override protected def configureHttp(router: HttpRouter): Unit = {
    import com.twitter.finagle.http.filter.Cors
    router
      .filter(
        new Cors.HttpFilter(
          Cors.Policy(
            allowsOrigin = _ => Some("*"),
            allowsMethods =
              _ => Some(Seq("HEAD", "GET", "PUT", "POST", "DELETE", "OPTIONS")),
            allowsHeaders = _ =>
              Some(
                Seq(
                  "Origin",
                  "X-Requested-With",
                  "Content-Type",
                  "Accept",
                  "Authorization"
                )
              ),
            supportsCredentials = true,
            maxAge = Some(1.day)
          )
        )
      )
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .exceptionMapper[PassThroughExceptionMapper]
      .add[PreflightController]
      .add[AuthController]
      .add[UserController]
      .add[SearchController]
      .add[PeopleController]
      .add[ThingController]
      .add[TvShowController]
      .add[MovieController]
      .add[MetadataController]
      .add[AvailabilityController]
      .add[AdminController]
  }

  override def postInjectorStartup(): Unit = {
    super.postInjectorStartup()

    injector.instance[TmdbBackgroundProcessor].run()
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
        val location = new File(
          s"${System.getProperty("java.io.tmpdir")}/db/migrate/postgres"
        )
        val gddl = new GenerateDdls()
        gddl.main(
          Array(
            new File(s"${location.getAbsolutePath}/V1__create.sql").getAbsolutePath
          )
        )
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
      case "generate-ddl"        => new GenerateDdls().main(rest)
      case "db-migrate"          => new RunDatabaseMigration().main(rest)
      case "import-movies"       => ImportMovies.main(rest)
      case "import-tv"           => ImportTv.main(rest)
      case "import-people"       => ImportPeople.main(rest)
      case "run-all-seeds"       => new RunAllSeeds().main(rest)
      case "seed-certifications" => SeedCertifications.main(rest)
      case "seed-genres"         => SeedGenres.main(rest)
      case "seed-networks"       => SeedNetworks.main(rest)
      case x =>
        Console.err.println(s"Unrecognized program: $x")
        sys.exit(1)
    }
  }
}
