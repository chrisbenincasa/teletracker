package com.chrisbenincasa.services.teletracker.auth

import com.chrisbenincasa.services.teletracker.db.model.{User, UserRow}
import com.twitter.finagle.http.Request

case class AuthenticatedUserContext(user: User)

object RequestContext {
  private val authUserField = Request.Schema.newField[AuthenticatedUserContext]()

  implicit class RequestContextSyntax(request: Request) {
    def authContext: AuthenticatedUserContext = request.ctx(authUserField)
  }

  private[teletracker] def set(request: Request, user: UserRow): Unit = {
//    import Remote._
//
//    val prefix = Headers.PREFIX
//
//    val curalateHeaders = request.headerMap.filter(m => m._1.startsWith(Headers.PREFIX))
//
//    context.setFromPrefix(prefix, curalateHeaders.toMap)
//
//    val contextMap = context.toMap
//
//    val data = CurrentRequestContext(
//      experimentId = ContextValue.find(Experimental, contextMap),
//      caller = ContextValue.find(Caller, contextMap),
//      raw = context
//    )
//
//    setCallerMdc(data.caller)
    request.ctx.update(authUserField, AuthenticatedUserContext(user.toFull))
  }
}