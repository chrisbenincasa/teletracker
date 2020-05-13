package com.teletracker.common.tasks.storage

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.ElasticsearchExecutor
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentType
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

object TaskRecordStore {
  final private val TableName = "teletracker.qa.tasks"
}

class TaskRecordStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext) {

  def recordNewTask(taskRecord: TaskRecord) = {
    elasticsearchExecutor.index(makeIndexRequestForRecord(taskRecord))
  }

  def recordNewTasks(taskRecords: Seq[TaskRecord]) = {
    if (taskRecords.isEmpty) {
      Future.successful(new BulkResponse(Array.empty, 0))
    } else {
      val request = new BulkRequest()
      taskRecords.map(makeIndexRequestForRecord).foreach(request.add)

      elasticsearchExecutor.bulk(request)
    }
  }

  private def makeIndexRequestForRecord(taskRecord: TaskRecord) = {
    new IndexRequest(teletrackerConfig.elasticsearch.tasks_index_name)
      .create(true)
      .id(taskRecord.id.toString)
      .source(taskRecord.asJson.noSpaces, XContentType.JSON)
  }

  def upsertTask(taskRecord: TaskRecord) = {
    elasticsearchExecutor.update(
      new UpdateRequest(
        teletrackerConfig.elasticsearch.tasks_index_name,
        taskRecord.id.toString
      ).doc(taskRecord.asJson.noSpaces, XContentType.JSON)
        .upsert(taskRecord.asJson.noSpaces, XContentType.JSON)
    )
  }

  def setTaskStarted(taskRecord: TaskRecord) = {
    upsertTask(
      taskRecord
        .copy(status = TaskStatus.Executing, startedAt = Some(Instant.now()))
    )
  }

  def setTaskSuccess(taskRecord: TaskRecord) = {
    upsertTask(
      taskRecord
        .copy(status = TaskStatus.Completed, finishedAt = Some(Instant.now()))
    )
  }

  def setTaskFailed(taskRecord: TaskRecord) = {
    upsertTask(
      taskRecord
        .copy(status = TaskStatus.Failed, finishedAt = Some(Instant.now()))
    )
  }
}
