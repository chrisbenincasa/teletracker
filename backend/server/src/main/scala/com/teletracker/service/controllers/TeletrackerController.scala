package com.teletracker.service.controllers

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.monitoring.Timing
import com.teletracker.common.util.UuidOrT
import com.teletracker.service.api.ListsApi
import com.twitter.finatra.http.response.ResponseBuilder
import scala.concurrent.{ExecutionContext, Future}

abstract class TeletrackerController(
  listsApi: ListsApi
)(implicit executionContext: ExecutionContext)
    extends BaseController {

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
