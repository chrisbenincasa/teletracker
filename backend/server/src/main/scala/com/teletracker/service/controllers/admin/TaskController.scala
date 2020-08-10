package com.teletracker.service.controllers.admin

import com.teletracker.common.model.DataResponse
import com.teletracker.common.tasks.storage.{
  TaskRecordQueryRequest,
  TaskRecordStore,
  TaskStatus
}
import com.teletracker.service.auth.AdminFilter
import com.teletracker.service.controllers.BaseController
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.Try

class TaskController @Inject()(
  taskRecordStore: TaskRecordStore
)(implicit executionContext: ExecutionContext)
    extends BaseController {
  import cats.instances.all._
  import com.teletracker.common.util.Monoidal._

  filter[AdminFilter].prefix("/api/v1/internal/tasks") {
    get("/?") { req: SearchTaskRequest =>
      val sort = req.sort
        .map(
          s =>
            TaskRecordQueryRequest.SortField.parse(s) match {
              case Some(value) =>
                value
              case None =>
                throw new IllegalArgumentException(s"Unrecognized sort: ${s}")
            }
        )
        .getOrElse(TaskRecordQueryRequest.StartedAtSort)

      val request =
        TaskRecordQueryRequest(
          taskName = req.taskName,
          statuses = req.status.ifEmptyOption
            .map(
              _.flatMap(
                statusString =>
                  Try(TaskStatus.fromString(statusString)).toOption
              )
            ),
          sort = sort,
          desc = req.desc,
          limit = req.limit
        )

      taskRecordStore
        .query(request)
        .map(records => {
          response
            .ok(
              DataResponse(records).printCompact
            )
            .contentTypeJson()
        })
    }

    get("/:taskId") { req: GetTaskRequest =>
      taskRecordStore.lookup(req.taskId).map {
        case Some(value) =>
          response.okCirceJsonResponse(DataResponse(value))
        case None =>
          response.notFound
      }
    }
  }
}

case class GetTaskRequest(@RouteParam taskId: UUID)

case class SearchTaskRequest(
  @QueryParam taskName: Option[String],
  @QueryParam(commaSeparatedList = true) status: Set[String] = Set(),
  @QueryParam limit: Int,
  @QueryParam sort: Option[String],
  @QueryParam desc: Boolean = true)
