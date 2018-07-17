package com.chrisbenincasa.services.teletracker.auth

import com.chrisbenincasa.services.teletracker.config.TeletrackerConfig
import com.chrisbenincasa.services.teletracker.db.UsersDbAccess
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.logging.Logger
import com.twitter.util.{Future, Promise}
import io.jsonwebtoken.{Jwts, SignatureException}
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class JwtAuthFilter @Inject()(
  config: TeletrackerConfig,
  usersDbAccess: UsersDbAccess
)(implicit executionContext: ExecutionContext) extends SimpleFilter[Request, Response] {
  private val logger = Logger(getClass)
  private val AuthHeaderRegex = """(\S+)\s+(\S+)""".r

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val token = request.params.get("token").
      orElse(request.headerMap.get("Authorization").flatMap(extractAuthHeaderValue(_, "bearer")))

    token match {
      case None => Future.value(Response(Status.Unauthorized))
      case Some(t) =>
        Try {
          Jwts.parser().
            setSigningKey(config.auth.jwt.secret.getBytes()).
            parseClaimsJws(t)
        } match {
          case Success(parsed) =>
            val respPromise = Promise[Response]()
            val foundUserFut = usersDbAccess.findByEmail(parsed.getBody.getSubject)

            foundUserFut.onComplete {
              case Success(Some(u)) =>
                RequestContext.set(request, u)
                respPromise.become(service(request))
              case Success(None) =>
                println(s"could not find user = ${parsed.getBody.getSubject}")
                respPromise.setValue(Response(Status.Unauthorized))
              case Failure(e) =>
                logger.error(e.getMessage, e)
                respPromise.setValue(Response(Status.InternalServerError))
            }

            respPromise
          case Failure(_: SignatureException) =>
            logger.error("Invalid JWT token")
            Future.value(Response(Status.Unauthorized))

          case Failure(e) =>
            logger.error(e.getMessage, e)
            Future.value(Response(Status.InternalServerError))
        }
    }
  }

  private def extractAuthHeaderValue(header: String, scheme: String): Option[String] = {
    header match {
      case AuthHeaderRegex(s, v) if s.equalsIgnoreCase(scheme) => Some(v)
      case _ => None
    }
  }
}
