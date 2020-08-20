package com.teletracker.service.controllers.admin

import com.teletracker.common.model.DataResponse
import com.teletracker.common.pubsub.TaskScheduler
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.tasks.storage.{
  TaskRecordQueryRequest,
  TaskRecordStore,
  TaskStatus
}
import com.teletracker.service.auth.AdminFilter
import com.teletracker.service.controllers.{BaseController, InjectedRequest}
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.circe.Json
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TaskController @Inject()(
  taskRecordStore: TaskRecordStore,
  taskScheduler: TaskScheduler
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

    post("/:taskId/reschedule") { req: RescheduleTaskRequest =>
      import io.circe.parser._

      decode[RescheduleTaskRequestBody](req.request.contentString) match {
        case Left(value) =>
          logger.error("Error deserializing body", value)
          Future.successful(response.internalServerError)

        case Right(body) =>
          taskRecordStore.lookup(req.taskId).flatMap {
            case Some(value) =>
              taskScheduler.schedule(value.asMessage(body.overrideArgs))
            case None => Future.successful(response.notFound)
          }
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

case class RescheduleTaskRequest(
  @QueryParam taskId: UUID,
  request: Request)
    extends InjectedRequest

@JsonCodec(decodeOnly = true)
case class RescheduleTaskRequestBody(overrideArgs: Option[Json])
