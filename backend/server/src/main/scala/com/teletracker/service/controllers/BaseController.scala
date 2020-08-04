package com.teletracker.service.controllers

import com.teletracker.common.util.json.circe.JsonExtensions
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import io.circe.Encoder

trait BaseController extends Controller with JsonExtensions {
  implicit def toRichInjectedRequest(re: InjectedRequest): RichInjectedRequest =
    new RichInjectedRequest(re)

  implicit def toRichRegularRequest(re: Request): RichInjectedRequest =
    toRichInjectedRequest(new InjectedRequest {
      override def request: Request = re
    })

  implicit def toRichCircefiedResponse(
    response: ResponseBuilder
  ): RichCircefiedResponse =
    new RichCircefiedResponse(response)
}

final class RichInjectedRequest(val r: InjectedRequest) extends AnyVal {
  import com.teletracker.service.auth.RequestContext._

  def authenticatedUserId: Option[String] = r.request.authContext.map(_.userId)
}

final class RichCircefiedResponse(val r: ResponseBuilder) extends AnyVal {
  import JsonExtensions._

  def okCirceJsonResponse[T: Encoder](
    obj: T
  ): ResponseBuilder#EnrichedResponse =
    r.ok(obj.printCompact).contentTypeJson()
}
