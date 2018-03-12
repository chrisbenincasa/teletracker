package com.chrisbenincasa.teletracker.server.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class ExampleController extends Controller {
  get("/api/v1/version") { _: Request =>
    val ret = Map("version" -> "0.1.0")
    response.ok(ret)
  }
}
