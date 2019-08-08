package com.teletracker.service.auth

import com.teletracker.common.config.TeletrackerConfig
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AdminFilter @Inject()(
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends SimpleFilter[Request, Response] {

  override def apply(
    request: Request,
    service: Service[Request, Response]
  ): Future[Response] = {
    request.headerMap.get("Authorization") match {
      case Some(header) =>
        JwtAuthFilter.extractAuthHeaderValue(header, "bearer") match {
          case Some(token)
              if teletrackerConfig.auth.admin.adminKeys.contains(token) =>
            service(request)

          case _ =>
            Future.value(Response(Status.Unauthorized))
        }

      case None => Future.value(Response(Status.Unauthorized))
    }
  }
}
