package com.teletracker.service

import com.google.inject.Module
import com.teletracker.common.util.{GenreCache, NetworkCache}
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.controllers._
import com.teletracker.service.exception_mappers.PassThroughExceptionMapper
import com.teletracker.service.filters.OpenCensusMonitoringFilter
import com.teletracker.service.inject.ServerModules
import com.teletracker.service.util.json.JsonModule
import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.ssl.{ClientAuth, Engine}
import com.twitter.finagle.ssl.server.SslServerConfiguration
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{
  LoggingMDCFilter,
  StatsFilter,
  TraceIdMDCFilter
}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.inject.Logging
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.{
  InsecureTrustManagerFactory,
  SelfSignedCertificate
}
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global

object TeletrackerServerMain extends TeletrackerServer

class TeletrackerServer(
  override protected val modules: Seq[Module] = ServerModules())
    extends HttpServer
    with Logging {

  postmain {
    injector.instance[NetworkCache].getAllNetworks()
    injector.instance[GenreCache].get()
  }

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
      .filter[OpenCensusMonitoringFilter]
      .filter[StatsFilter[Request]]
      .filter[JwtAuthFilter]
      .exceptionMapper[PassThroughExceptionMapper]
      .add[PreflightController]
      .add[AuthController]
      .add[UserController]
      .add[SearchController]
      .add[ItemsController]
      .add[MetadataController]
      .add[AvailabilityController]
      .add[AdminController]
      .add[HealthController]
      .add[InternalController]
      .add[PopularItemsController]
  }

  override protected def configureHttpsServer(
    server: Http.Server
  ): Http.Server = {
    val allocator = io.netty.buffer.UnpooledByteBufAllocator.DEFAULT

    server.withTransport.tls(
      SslServerConfiguration(clientAuth = ClientAuth.Wanted),
      (_: SslServerConfiguration) => {
        val context = SslContextBuilder
          .forServer(
            new File(
              s"${System.getenv("LOCALCERTS_PATH")}/server.crt"
            ),
            new File(
              s"${System.getenv("LOCALCERTS_PATH")}/server.key"
            ),
            null
          )
          .trustManager(InsecureTrustManagerFactory.INSTANCE)
          .clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL)
          .build()
        val engine = context.newEngine(allocator)
        engine.setNeedClientAuth(false)
        Engine(engine)
      }
    )
  }
}

object Teletracker extends com.twitter.inject.app.App {
  override protected def modules: Seq[Module] =
    Seq(com.google.inject.util.Modules.EMPTY_MODULE)

  override protected def run(): Unit = {
    val cmd = args.headOption.getOrElse("server")
    val rest = if (args.nonEmpty) args.tail else Array.empty[String]

    cmd match {
      case "server" => new TeletrackerServer().main(rest)
//      case "reset-db" =>
//        val location = new File(
//          s"${System.getProperty("java.io.tmpdir")}/db/migrate/postgres"
//        )
//        val gddl = new GenerateDdls()
//        gddl.main(
//          Array(
//            new File(s"${location.getAbsolutePath}/V1__create.sql").getAbsolutePath
//          )
//        )
//        Await.result(gddl)
//
//        val clean = new RunDatabaseMigration()
//        clean.main(Array("-action=clean"))
//        Await.result(clean)
//
//        val migrate = new RunDatabaseMigration()
//        migrate.main(Array("-action=migrate", s"-loc=$location"))
//        Await.result(migrate)
//
//        injector.instance[RunAllSeedsTask].run()
//
//        close()
//      case "generate-ddl"        => new GenerateDdls().main(rest)
//      case "db-migrate"          => new RunDatabaseMigration().main(rest)
//      case "import-movies"       => ImportMovies.main(rest)
//      case "import-tv"           => ImportTv.main(rest)
//      case "import-people"       => ImportPeople.main(rest)
//      case "run-all-seeds"       => RunAllSeedsMain.main(rest)
//      case "seed-certifications" => SeedCertifications.main(rest)
//      case "seed-genres"         => SeedGenres.main(rest)
//      case "seed-networks"       => SeedNetworks.main(rest)
      case x =>
        Console.err.println(s"Unrecognized program: $x")
        sys.exit(1)
    }
  }
}
