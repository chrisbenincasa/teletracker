package com.teletracker.service.controllers

import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.auth.{JwtAuthFilter, PasswordAuthFilter}
import com.teletracker.service.config.TeletrackerConfig
import com.teletracker.service.db.UsersDbAccess
import com.teletracker.service.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AuthController @Inject()(
  config: TeletrackerConfig,
  usersDbAccess: UsersDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext)
    extends Controller {

  prefix("/api/v1/auth") {
    // Log in user based on creds
    filter[PasswordAuthFilter].post("/login") { req: LoginRequest =>
      usersDbAccess
        .vendToken(req.email)
        .map(token => {
          response.ok(
            DataResponse(
              CreateUserResponse(req.request.authContext.user.id, token)
            )
          )
        })
    }

    filter[JwtAuthFilter].get("/status") { req: Request =>
      response.ok(
        DataResponse(AuthenticatedResponse(true, req.authContext.user.email))
      )
    }

    // Log current user out
    filter[JwtAuthFilter].post("/logout") { req: Request =>
      // Revoke token?
      usersDbAccess
        .revokeToken(req.authContext.user.id, req.authToken.token)
        .map(_ => {
          response.ok
        })
    }
  }
}

case class CreateUserRequest(
  email: String,
  username: String,
  name: String,
  password: String)

case class LoginRequest(
  request: Request,
  email: String,
  password: String,
  redirect_url: Option[String])

case class AuthenticatedResponse(
  authenticated: Boolean,
  email: String)
