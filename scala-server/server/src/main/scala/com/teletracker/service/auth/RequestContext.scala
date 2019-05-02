package com.teletracker.service.auth

import com.teletracker.service.db.model.{User, UserRow}
import com.twitter.finagle.http.Request

case class AuthenticatedUserContext(user: User)
case class AuthenticatedTokenContext(token: String)

object RequestContext {
  private val authUserField = Request.Schema.newField[AuthenticatedUserContext]()
  private val authTokenField = Request.Schema.newField[AuthenticatedTokenContext]()

  implicit class RequestContextSyntax(request: Request) {
    def authContext: AuthenticatedUserContext = request.ctx(authUserField)
    def authToken = request.ctx(authTokenField)
  }

  private[teletracker] def set(request: Request, user: UserRow, token: String): Unit = {
    set(request, user)
    request.ctx.update(authTokenField, AuthenticatedTokenContext(token))
  }

  private[teletracker] def set(request: Request, user: UserRow): Unit = {
    request.ctx.update(authUserField, AuthenticatedUserContext(user.toFull))
  }
}