package com.teletracker.service.controllers

import com.teletracker.common.auth.jwt.JwtVendor
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.UsersDbAccess
import com.teletracker.service.api.UsersApi
import com.teletracker.service.auth.{JwtAuthExtractor, JwtAuthFilter}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AuthController @Inject()(
  config: TeletrackerConfig,
  usersApi: UsersApi,
  usersDbAccess: UsersDbAccess,
  jwtVendor: JwtVendor,
  jwtAuthExtractor: JwtAuthExtractor
)(implicit executionContext: ExecutionContext)
    extends Controller {

  prefix("/api/v1/auth") {
    filter[JwtAuthFilter].get("/status") { _: Request =>
      response.ok
    }
  }
}

case class LoginRequest(
  request: Request,
  email: String,
  password: String,
  redirect_url: Option[String])

case class AuthenticatedResponse(
  authenticated: Boolean,
  email: String)
