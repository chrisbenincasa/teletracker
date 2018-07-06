package com.chrisbenincasa.services.teletracker

import com.chrisbenincasa.services.teletracker.controllers.{AuthController, SearchController, TvShowController, UserController}
import com.chrisbenincasa.services.teletracker.inject.Modules
import com.chrisbenincasa.services.teletracker.util.json.JsonModule
import com.google.inject.Module
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.inject.Logging
import scala.concurrent.ExecutionContext.Implicits.global

object TeletrackerServerMain extends TeletrackerServer

class TeletrackerServer extends HttpServer with Logging  {
  override protected def defaultFinatraHttpPort: String = ":3000"

  override protected def modules: Seq[Module] = Modules()

  override protected def jacksonModule: Module = new JsonModule

  override protected def configureHttp(router: HttpRouter): Unit = {
    router.
      filter[LoggingMDCFilter[Request, Response]].
      filter[TraceIdMDCFilter[Request, Response]].
      add[AuthController].
      add[UserController].
      add[SearchController].
      add[TvShowController]
  }
}
