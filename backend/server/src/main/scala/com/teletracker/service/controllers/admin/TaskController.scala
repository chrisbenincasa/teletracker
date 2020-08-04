package com.teletracker.service.controllers.admin

import com.teletracker.common.model.DataResponse
import com.teletracker.common.tasks.storage.{
  TaskRecordQueryRequest,
  TaskRecordStore
}
import com.teletracker.service.auth.AdminFilter
import com.teletracker.service.controllers.BaseController
import com.twitter.finagle.http.Request
import com.twitter.finatra.request.QueryParam
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TaskController @Inject()(
  taskRecordStore: TaskRecordStore
)(implicit executionContext: ExecutionContext)
    extends BaseController {
  filter[AdminFilter].prefix("/api/v1/internal/tasks") {
    get("/?") { req: SearchTaskRequest =>
      taskRecordStore
        .query(TaskRecordQueryRequest(limit = req.limit))
        .map(records => {
          response
            .ok(
              DataResponse(records).printCompact
            )
            .contentTypeJson()
        })
    }
  }
}

case class SearchTaskRequest(@QueryParam limit: Int)
