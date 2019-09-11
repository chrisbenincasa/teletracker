package com.teletracker.service.auth

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future

/**
  * Must run after JwtAuthFilter
  */
class AuthRequiredFilter extends SimpleFilter[Request, Response] {
  import RequestContext._

  override def apply(
    request: Request,
    service: Service[Request, Response]
  ): Future[Response] = {
    if (request.authContext.isEmpty || request.authToken.isEmpty) {
      Future.value(Response(Status.Unauthorized))
    } else {
      service(request)
    }
  }
}
