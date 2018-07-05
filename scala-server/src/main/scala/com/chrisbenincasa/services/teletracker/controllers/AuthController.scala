package com.chrisbenincasa.services.teletracker.controllers

import com.chrisbenincasa.services.teletracker.auth.RequestContext._
import com.chrisbenincasa.services.teletracker.auth.jwt.JwtVendor
import com.chrisbenincasa.services.teletracker.auth.{JwtAuthFilter, PasswordAuthFilter}
import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.chrisbenincasa.services.teletracker.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AuthController @Inject()(
  config: TeletrackerConfig,
  usersDbAccess: UsersDbAccess,
  jwtVendor: JwtVendor
)(implicit executionContext: ExecutionContext) extends Controller {

  prefix("/api/v1/auth") {
    // Log in user based on creds
    filter[PasswordAuthFilter].post("/login") { req: LoginRequest =>
      // Retrieve existing token?
      response.ok(
        DataResponse(
          TokenResponse(jwtVendor.vend(req.email))
        )
      )
    }

    filter[JwtAuthFilter].get("/status") { req: Request =>
      response.ok(DataResponse(AuthenticatedResponse(true, req.authContext.user.email)))
    }

    // Log current user out
    filter[JwtAuthFilter].post("/logout") { _: Request =>
      // Revoke token?
      response.ok
    }
  }
}

case class CreateUserRequest(
  email: String,
  username: String,
  name: String,
  password: String
)

case class LoginRequest(
  email: String,
  password: String,
  redirect_url: Option[String]
)

case class AuthenticatedResponse(
  authenticated: Boolean,
  email: String
)
