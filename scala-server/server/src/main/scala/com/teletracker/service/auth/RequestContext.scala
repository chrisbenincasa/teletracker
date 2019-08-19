package com.teletracker.service.auth

import com.twitter.finagle.http.Request

case class AuthenticatedUserContext(userId: String)
case class AuthenticatedTokenContext(token: String)

object RequestContext {
  private val authUserField =
    Request.Schema.newField[AuthenticatedUserContext]()
  private val authTokenField =
    Request.Schema.newField[AuthenticatedTokenContext]()

  implicit class RequestContextSyntax(request: Request) {
    def authContext: AuthenticatedUserContext = request.ctx(authUserField)
    def authToken: AuthenticatedTokenContext = request.ctx(authTokenField)
  }

  private[teletracker] def set(
    request: Request,
    userId: String,
    token: String
  ): Unit = {
    set(request, userId)
    request.ctx.update(authTokenField, AuthenticatedTokenContext(token))
  }

  private[teletracker] def set(
    request: Request,
    userId: String
  ): Unit = {
    request.ctx.update(authUserField, AuthenticatedUserContext(userId))
  }
}
