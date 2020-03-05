package com.teletracker.service.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HealthController @Inject()()(implicit executionContext: ExecutionContext)
    extends Controller {
  get("/health") { _: Request =>
    response.ok(Map("status" -> "OK"))
  }
}
