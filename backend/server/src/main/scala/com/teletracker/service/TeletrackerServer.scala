package com.teletracker.service

import com.google.inject.Module
import com.teletracker.common.util.{GenreCache, NetworkCache}
import com.teletracker.service.auth.JwtAuthFilter
import com.teletracker.service.controllers._
import com.teletracker.service.exception_mappers.PassThroughExceptionMapper
import com.teletracker.service.filters.CorsFilter
import com.teletracker.service.inject.ServerModules
import com.teletracker.service.util.json.JsonModule
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.ssl.server.SslServerConfiguration
import com.twitter.finagle.ssl.{ClientAuth, Engine}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{
  CommonFilters,
  LoggingMDCFilter,
  TraceIdMDCFilter
}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.inject.Logging
import com.twitter.inject.requestscope.FinagleRequestScopeFilter
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
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
    router
      .filter(CorsFilter.instance)
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CommonFilters]
      .filter[FinagleRequestScopeFilter[Request, Response]]
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
      .add[ListController]
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
              s"${System.getenv("LOCALCERTS_PATH")}/fullchain.pem"
            ),
            new File(
              s"${System.getenv("LOCALCERTS_PATH")}/privkey.pem"
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
