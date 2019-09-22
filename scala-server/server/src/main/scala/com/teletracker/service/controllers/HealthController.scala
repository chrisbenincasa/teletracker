package com.teletracker.service.controllers

import com.teletracker.common.db.access.HealthCheckDbAccess
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HealthController @Inject()(
  healthCheckDbAccess: HealthCheckDbAccess
)(implicit executionContext: ExecutionContext)
    extends Controller {
  get("/health") { _: Request =>
    healthCheckDbAccess.ping
      .map(_ => {
        response.ok(Map("status" -> "OK"))
      })
      .recover {
        case e =>
          response.internalServerError
      }
  }
}
