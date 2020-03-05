package com.teletracker.service.auth

import com.teletracker.common.config.TeletrackerConfig
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import javax.inject.Inject
import java.util.Base64
import scala.concurrent.ExecutionContext

class AdminFilter @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends SimpleFilter[Request, Response] {

  override def apply(
    request: Request,
    service: Service[Request, Response]
  ): Future[Response] = {
    getParamOrHeader(request) match {
      case Some(token)
          if teletrackerConfig.auth.admin.adminKeys.contains(token) =>
        service(request)

      case _ =>
        Future.value(Response(Status.Unauthorized))
    }
  }

  private def getParamOrHeader(request: Request): Option[String] = {
    Option(request.getParam("admin_key"))
      .map(base64Encoded => {
        new String(Base64.getDecoder.decode(base64Encoded))
      })
      .orElse {
        request.headerMap
          .get("Authorization")
          .flatMap(header => {
            JwtAuthFilter.extractAuthHeaderValue(header, "bearer")
          })
      }
  }
}
