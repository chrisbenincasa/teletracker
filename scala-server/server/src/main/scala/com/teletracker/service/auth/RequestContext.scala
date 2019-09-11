package com.teletracker.service.auth

import com.twitter.finagle.http.Request

case class AuthenticatedUserContext(userId: String)
case class AuthenticatedTokenContext(token: String)

object RequestContext {
  private val authUserField =
    Request.Schema.newField[Option[AuthenticatedUserContext]]()
  private val authTokenField =
    Request.Schema.newField[Option[AuthenticatedTokenContext]]()

  implicit class RequestContextSyntax(request: Request) {
    def authContext: Option[AuthenticatedUserContext] =
      request.ctx(authUserField)
    def authToken: Option[AuthenticatedTokenContext] =
      request.ctx(authTokenField)
  }

  private[teletracker] def init(request: Request): Unit = {
    request.ctx.update(authTokenField, None)
    request.ctx.update(authUserField, None)
  }

  private[teletracker] def set(
    request: Request,
    userId: String,
    token: String
  ): Unit = {
    set(request, userId)
    request.ctx.update(authTokenField, Some(AuthenticatedTokenContext(token)))
  }

  private[teletracker] def set(
    request: Request,
    userId: String
  ): Unit = {
    request.ctx.update(authUserField, Some(AuthenticatedUserContext(userId)))
  }
}
