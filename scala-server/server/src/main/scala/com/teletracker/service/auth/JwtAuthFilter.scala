package com.teletracker.service.auth

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.access.UsersDbAccess
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.logging.Logger
import com.twitter.util.{Future, Promise}
import io.jsonwebtoken.{
  Claims,
  ExpiredJwtException,
  Jws,
  Jwts,
  MalformedJwtException,
  SignatureException,
  UnsupportedJwtException
}
import javax.inject.Inject
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
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

class JwtAuthExtractor @Inject()(
  config: TeletrackerConfig,
  googlePublicKeyRetriever: GooglePublicKeyRetriever
)(implicit executionContext: ExecutionContext) {
  import com.teletracker.service.auth.JwtAuthFilter.extractAuthHeaderValue

  private val certificateFactory = CertificateFactory.getInstance("X.509")

  def extractToken(request: Request): Option[String] = {
    request.params
      .get("token")
      .orElse(
        request.headerMap
          .get("Authorization")
          .flatMap(extractAuthHeaderValue(_, "bearer"))
      )
  }

  def parseToken(token: String): SFuture[Try[Jws[Claims]]] = {
    googlePublicKeyRetriever
      .getPublicCerts()
      .map(certs => {
        if (certs.isEmpty) {
          throw new IllegalStateException(
            "Could not retrieve public certs from googleapis.com!!!"
          )
        }

        certs.values
          .map(cert => {
            val certificate = certificateFactory
              .generateCertificate(new ByteArrayInputStream(cert.getBytes()))

            () =>
              Try(
                Jwts
                  .parser()
                  .setSigningKey(certificate.getPublicKey)
                  .parseClaimsJws(token)
              )
          })
          .reduce((l, r) => () => l().orElse(r()))
      })
      .map(_())
  }

  def extractAndParse(request: Request): SFuture[Try[Jws[Claims]]] = {
    extractToken(request) match {
      case Some(token) => parseToken(token)
      case None        => SFuture.successful(Failure(TokenNotFoundException))
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
    RequestContext.init(request)

    extractor.extractToken(request) match {
      case None =>
        service(request)

      case Some(t) =>
        val respPromise = Promise[Response]()
        extractor.parseToken(t).transform(_.flatten).andThen {
          case Success(parsed) =>
            RequestContext.set(request, parsed.getBody.getSubject, t)
            respPromise.become(service(request))

          case Failure(
              e @ (_: SignatureException | _: UnsupportedJwtException |
              _: MalformedJwtException | _: ExpiredJwtException)
              ) =>
            logger.error(s"Invalid JWT token: ${e.getMessage}")
            respPromise.setValue(Response(Status.Unauthorized))

          case Failure(e) =>
            logger.error(e.getMessage, e)
            respPromise.setValue(Response(Status.InternalServerError))
        }

        respPromise
    }
  }
}
