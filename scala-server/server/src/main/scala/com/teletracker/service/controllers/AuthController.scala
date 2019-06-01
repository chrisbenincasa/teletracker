package com.teletracker.service.controllers

import com.teletracker.service.auth.RequestContext._
import com.teletracker.service.auth.jwt.JwtVendor
import com.teletracker.service.auth.{
  JwtAuthExtractor,
  JwtAuthFilter,
  PasswordAuthFilter,
  TokenNotFoundException
}
import com.teletracker.service.config.TeletrackerConfig
import com.teletracker.service.db.UsersDbAccess
import com.teletracker.service.model.DataResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import io.jsonwebtoken.ExpiredJwtException
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class AuthController @Inject()(
  config: TeletrackerConfig,
  usersDbAccess: UsersDbAccess,
  jwtVendor: JwtVendor,
  jwtAuthExtractor: JwtAuthExtractor
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
    post("/logout") { request: Request =>
      def revoke(
        userId: Int,
        token: String
      ): Future[ResponseBuilder#EnrichedResponse] = {
        usersDbAccess
          .revokeToken(userId, token)
          .map(_ => {
            response.ok
          })
      }

      jwtAuthExtractor.extractToken(request) match {
        case None => Future.successful(response.badRequest)
        case Some(token) =>
          jwtAuthExtractor.parseToken(token) match {
            case Success(value) =>
              usersDbAccess.findByEmail(value.getBody.getSubject).flatMap {
                case None       => Future.successful(response.badRequest)
                case Some(user) => revoke(user.id.get, token)
              }

            case Failure(ex: ExpiredJwtException) =>
              usersDbAccess.findByEmail(ex.getClaims.getSubject).flatMap {
                case None       => Future.successful(response.badRequest)
                case Some(user) => revoke(user.id.get, token)
              }

            case Failure(NonFatal(e)) =>
              Future.successful(response.internalServerError)
            case Failure(ex) => throw ex
          }
      }
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
