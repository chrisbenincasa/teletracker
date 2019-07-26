package com.teletracker.service.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller

class HealthController extends Controller {
  get("/health") { _: Request =>
    response.ok(Map("status" -> "OK"))
  }
}
