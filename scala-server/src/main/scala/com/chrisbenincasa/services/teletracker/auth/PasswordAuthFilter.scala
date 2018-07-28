package com.chrisbenincasa.services.teletracker.auth

import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.Try

class PasswordAuthFilter @Inject()(
  mapper: ObjectMapper with ScalaObjectMapper,
  usersDbAccess: UsersDbAccess
)(implicit executionContext: ExecutionContext) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val responsePromise = com.twitter.util.Promise[Response]()

    Promise.fromTry(extractUserDetails(request)).future.flatMap(userDetails => {
      usersDbAccess.findByEmail(userDetails.email).map {
        case Some(u) if PasswordHash.validatePassword(userDetails.password, u.password) =>
          RequestContext.set(request, u)
          responsePromise.become(service(request))

        case Some(_) | None => responsePromise.setValue(Response(Status.Unauthorized))
      }
    }).recover {
      case e =>
        // Log
        e.printStackTrace()
        responsePromise.setValue(Response(Status.BadRequest))
    }

    responsePromise
  }

  private def extractUserDetails(request: Request): Try[UserDetails] = {
    Try(mapper.readValue(request.contentString, classOf[UserDetails]))
  }
}

case class UserDetails(email: String, password: String)