package com.chrisbenincasa.services.teletracker.auth

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import RequestContext._
import javax.inject.Inject
import scala.util.Try

class UserSelfOnlyFilter @Inject()() extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val userId = request.params.get("userId").orElse(request.params.get("user_id"))

    userId match {
      case Some("self") => service(request)
      case Some(id) if Try(id.toInt).isSuccess =>
        if (request.authContext.user.id == id.toInt) {
          service(request)
        } else {
          Future.value(Response(Status.Unauthorized))
        }
      case _ => Future.value(Response(Status.Unauthorized))
    }
  }
}
