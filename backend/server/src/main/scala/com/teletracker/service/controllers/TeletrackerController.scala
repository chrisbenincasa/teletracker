package com.teletracker.service.controllers

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.monitoring.Timing
import com.teletracker.common.util.UuidOrT
import com.teletracker.service.api.ListsApi
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import scala.concurrent.{ExecutionContext, Future}

object TeletrackerController {
  implicit def toRichInjectedRequest(re: InjectedRequest): RichInjectedRequest =
    new RichInjectedRequest(re)

  implicit def toRichRegularRequest(re: Request): RichInjectedRequest =
    toRichInjectedRequest(new InjectedRequest {
      override def request: Request = re
    })
}

abstract class TeletrackerController(
  listsApi: ListsApi
)(implicit executionContext: ExecutionContext)
    extends Controller {

  implicit def toRichInjectedRequest(re: InjectedRequest): RichInjectedRequest =
    new RichInjectedRequest(re)

  implicit def toRichRegularRequest(re: Request): RichInjectedRequest =
    toRichInjectedRequest(new InjectedRequest {
      override def request: Request = re
    })

  def withList(
    userId: String,
    listId: String
  )(
    f: StoredUserList => Future[ResponseBuilder#EnrichedResponse]
  ): Future[ResponseBuilder#EnrichedResponse] = {
    Timing.time("withList") {
      getListForId(userId, listId).flatMap {
        case None       => Future.successful(response.noContent)
        case Some(list) => f(list)
      }
    }
  }

  def getListForId(
    userId: String,
    listId: String
  ): Future[Option[StoredUserList]] = {
    UuidOrT.parse(listId, _.toInt).idOrFallback match {
      case Left(value) =>
        listsApi.findListForUser(value, Some(userId))

      case Right(value) =>
        listsApi.findListForUserLegacy(value, userId)
    }
  }
}

final class RichInjectedRequest(val r: InjectedRequest) extends AnyVal {
  import com.teletracker.service.auth.RequestContext._

  def authenticatedUserId: Option[String] = r.request.authContext.map(_.userId)
}
