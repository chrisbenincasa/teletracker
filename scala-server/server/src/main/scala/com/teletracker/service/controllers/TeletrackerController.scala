package com.teletracker.service.controllers

import com.teletracker.common.db.access.UsersDbAccess
import com.teletracker.common.db.model.TrackedListRow
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

object TeletrackerController {
  implicit def toRichInjectedRequest(re: InjectedRequest): RichInjectedRequest =
    new RichInjectedRequest(re)

  implicit def toRichRegularRequest(re: Request): RichInjectedRequest =
    toRichInjectedRequest(new InjectedRequest {
      override def request: Request = re
    })
}

abstract class TeletrackerController(
  usersDbAccess: UsersDbAccess
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
    f: TrackedListRow => Future[ResponseBuilder#EnrichedResponse]
  ): Future[ResponseBuilder#EnrichedResponse] = {
    getListForId(userId, listId).flatMap {
      case None       => Future.successful(response.noContent)
      case Some(list) => f(list)
    }
  }

  def getListForId(
    userId: String,
    listId: String
  ): Future[Option[TrackedListRow]] = {
    if (listId == "default") {
      usersDbAccess.findDefaultListForUser(userId)
    } else {
      Promise
        .fromTry(Try(listId.toInt))
        .future
        .flatMap(listId => {
          usersDbAccess
            .findListForUser(userId, listId)
        })
    }
  }
}

final class RichInjectedRequest(val r: InjectedRequest) extends AnyVal {
  import com.teletracker.service.auth.RequestContext._

  def authenticatedUserId: String = r.request.authContext.userId
}