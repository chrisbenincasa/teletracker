package com.teletracker.service.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class PreflightController extends Controller {
  options("/api/:*") {_: Request =>
    response.ok
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Allow-Methods", "HEAD, GET, PUT, POST, DELETE, OPTIONS")
      .header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization")
      .header("Access-Control-Max-Age", 86400)
      .contentType("application/json")
  }
}
