package com.chrisbenincasa.teletracker.server

import com.chrisbenincasa.teletracker.server.controllers.ExampleController
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.routing.HttpRouter

class Server extends HttpServer {
  override val defaultFinatraHttpPort: String = ":8080"

  override protected def configureHttp(router: HttpRouter): Unit = {
    router.add[ExampleController]
  }
}

object ServerMain extends Server
