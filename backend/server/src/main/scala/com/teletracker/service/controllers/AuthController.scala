package com.teletracker.service.controllers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.service.auth.{
  AuthRequiredFilter,
  CurrentAuthenticatedUser,
  JwtAuthExtractor
}
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.{Inject, Provider}
import scala.concurrent.ExecutionContext

class AuthController @Inject()(
  config: TeletrackerConfig,
  jwtAuthExtractor: JwtAuthExtractor,
  currentUser: Provider[Option[CurrentAuthenticatedUser]]
)(implicit executionContext: ExecutionContext)
    extends Controller {

  prefix("/api/v1/auth") {
    filter[AuthRequiredFilter].get("/status") { _: Request =>
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
