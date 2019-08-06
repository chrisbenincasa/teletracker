package com.teletracker.service.auth

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.UsersDbAccess
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.logging.Logger
import com.twitter.util.{Future, Promise}
import io.jsonwebtoken.{Claims, Jws, Jwts, SignatureException}
import javax.inject.Inject
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future => SFuture}
import scala.util.{Failure, Success, Try}

object JwtAuthFilter {
  private val AuthHeaderRegex = """(\S+)\s+(\S+)""".r

  def extractAuthHeaderValue(
    header: String,
    scheme: String
  ): Option[String] = {
    header match {
      case AuthHeaderRegex(s, v) if s.equalsIgnoreCase(scheme) => Some(v)
      case _                                                   => None
    }
  }
}

class JwtAuthExtractor @Inject()(config: TeletrackerConfig) {
  import com.teletracker.service.auth.JwtAuthFilter.extractAuthHeaderValue

  def extractToken(request: Request): Option[String] = {
    request.params
      .get("token")
      .orElse(
        request.headerMap
          .get("Authorization")
          .flatMap(extractAuthHeaderValue(_, "bearer"))
      )
  }

  def parseToken(token: String): Try[Jws[Claims]] = {
    Try {
      Jwts
        .parser()
        .setSigningKey(config.auth.jwt.secret.getBytes())
        .parseClaimsJws(token)
    }
  }

  def extractAndParse(request: Request): Try[Jws[Claims]] = {
    extractToken(request) match {
      case Some(token) => parseToken(token)
      case None        => Failure(TokenNotFoundException)
    }
  }
}

object TokenNotFoundException extends Exception("No token found in request")

class JwtAuthFilter @Inject()(
  config: TeletrackerConfig,
  usersDbAccess: UsersDbAccess,
  extractor: JwtAuthExtractor
)(implicit executionContext: ExecutionContext)
    extends SimpleFilter[Request, Response] {
  private val logger = Logger(getClass)

  override def apply(
    request: Request,
    service: Service[Request, Response]
  ): Future[Response] = {
    extractor.extractToken(request) match {
      case None => Future.value(Response(Status.Unauthorized))
      case Some(t) =>
        extractor.parseToken(t) match {
          case Success(parsed) =>
            val respPromise = Promise[Response]()

            usersDbAccess.isTokenRevoked(t).andThen {
              case Success(true) =>
                respPromise.setValue(Response(Status.Unauthorized))

              case Success(false) =>
                usersDbAccess.findByEmail(parsed.getBody.getSubject).andThen {
                  case Success(Some(u)) =>
                    RequestContext.set(request, u, t)
                    respPromise.become(service(request))

                  case Success(None) =>
                    logger.warn(
                      s"Could not find user = ${parsed.getBody.getSubject}"
                    )
                    respPromise.setValue(Response(Status.Unauthorized))

                  case Failure(e) =>
                    logger
                      .error("Unexpected error while finding user for token", e)
                    respPromise.setValue(Response(Status.InternalServerError))
                }

              case Failure(ex) =>
                logger.error(
                  "Unexpected error while checking revocation status",
                  ex
                )
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
}
