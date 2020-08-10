package com.teletracker.common.tasks.storage

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.{
  ElasticsearchAccess,
  ElasticsearchCrud,
  ElasticsearchExecutor
}
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.{
  MultiMatchQueryBuilder,
  Operator,
  QueryBuilders
}
import org.elasticsearch.script.{Script, ScriptType}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.{FieldSortBuilder, SortMode, SortOrder}
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

case class TaskRecordQueryRequest(
  taskName: Option[String] = None,
  statuses: Option[Set[TaskStatus]] = None,
  sort: TaskRecordQueryRequest.SortField = TaskRecordQueryRequest.StartedAtSort,
  desc: Boolean = true,
  limit: Int = 10)

object TaskRecordQueryRequest {
  val default: TaskRecordQueryRequest = TaskRecordQueryRequest()

  object SortField {
    def parse(input: String): Option[SortField] = {
      input match {
        case CreatedAtSort.field  => Some(CreatedAtSort)
        case StartedAtSort.field  => Some(StartedAtSort)
        case FinishedAtSort.field => Some(FinishedAtSort)
        case _                    => None
      }
    }
  }

  sealed trait SortField {
    def field: String
  }
  case object CreatedAtSort extends SortField {
    final val field: String = "createdAt"
  }
  case object StartedAtSort extends SortField {
    final val field: String = "startedAt"
  }
  case object FinishedAtSort extends SortField {
    final val field: String = "finishedAt"
  }
}

class TaskRecordStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  protected val elasticsearchExecutor: ElasticsearchExecutor
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchCrud[UUID, TaskRecord] {
  import com.teletracker.common.util.Functions._

  override protected def indexName: String =
    teletrackerConfig.elasticsearch.tasks_index_name

  def query(request: TaskRecordQueryRequest): Future[List[TaskRecord]] = {
    val query = QueryBuilders
      .boolQuery()
      .applyOptional(request.taskName)(
        (builder, name) =>
          builder.must(
            QueryBuilders
              .regexpQuery("taskName", s".*${name}.*")
          )
      )
      .applyOptional(request.statuses.filter(_.nonEmpty))(
        (builder, statuses) => {
          builder.must(
            statuses.foldLeft(QueryBuilders.boolQuery().minimumShouldMatch(1)) {
              case (b, status) =>
                b.should(QueryBuilders.termQuery("status", status.toString))
            }
          )
        }
      )

    val sortBuilder = new FieldSortBuilder(request.sort.field)
      .sortMode(SortMode.AVG)
      .order(if (request.desc) SortOrder.DESC else SortOrder.ASC)

    val source =
      new SearchSourceBuilder()
        .query(query)
        .size(request.limit)
        .sort(sortBuilder)

    println(source)

    val searchRequest = new SearchRequest(
      teletrackerConfig.elasticsearch.tasks_index_name
    ).source(source)
    elasticsearchExecutor
      .search(searchRequest)
      .map(decodeSearchResponse[TaskRecord])
  }

  def recordNewTask(taskRecord: TaskRecord): Future[IndexResponse] = {
    elasticsearchExecutor.index(makeIndexRequestForRecord(taskRecord))
  }

  def recordNewTasks(taskRecords: Seq[TaskRecord]): Future[BulkResponse] = {
    if (taskRecords.isEmpty) {
      Future.successful(new BulkResponse(Array.empty, 0))
    } else {
      val request = new BulkRequest()
      taskRecords.map(makeIndexRequestForRecord).foreach(request.add)

      elasticsearchExecutor.bulk(request)
    }
  }

  private def makeIndexRequestForRecord(
    taskRecord: TaskRecord
  ): IndexRequest = {
    new IndexRequest(teletrackerConfig.elasticsearch.tasks_index_name)
      .create(true)
      .id(taskRecord.id.toString)
      .source(taskRecord.asJson.noSpaces, XContentType.JSON)
  }

  def upsertTask(taskRecord: TaskRecord): Future[Option[TaskRecord]] = {
    elasticsearchExecutor
      .update(
        new UpdateRequest(
          teletrackerConfig.elasticsearch.tasks_index_name,
          taskRecord.id.toString
        ).doc(
            // Don't update createdAt timestamp
            taskRecord.copy(createdAt = None).asJson.noSpaces,
            XContentType.JSON
          )
          .upsert(taskRecord.asJson.noSpaces, XContentType.JSON)
          .fetchSource(true)
      )
      .map(response => {
        Option(response.getGetResult).flatMap(
          result => decodeSourceString[TaskRecord](result.sourceAsString())
        )
      })
  }

  def setTaskStarted(id: UUID): Future[Unit] = {
    val updateRequest = new UpdateRequest(
      teletrackerConfig.elasticsearch.tasks_index_name,
      id.toString
    ).script(
      new Script(
        ScriptType.INLINE,
        "painless",
        "ctx._source.status = params.status; ctx._source.startedAt = params.startedAt",
        Map[String, AnyRef](
          "status" -> TaskStatus.Executing.toString,
          "startedAt" -> Instant.now().toString
        ).asJava
      )
    )

    elasticsearchExecutor.update(updateRequest).map(_ => {})
  }

  def setTaskStarted(taskRecord: TaskRecord): Future[Option[TaskRecord]] = {
    upsertTask(
      taskRecord
        .copy(status = TaskStatus.Executing, startedAt = Some(Instant.now()))
    )
  }

  def setTaskSuccess(id: UUID): Future[Unit] = {
    setTaskFinished(id, TaskStatus.Completed)
  }

  def setTaskFailed(id: UUID): Future[Unit] = {
    setTaskFinished(id, TaskStatus.Failed)
  }

  private def setTaskFinished(
    id: UUID,
    status: TaskStatus
  ) = {
    val updateRequest = new UpdateRequest(
      teletrackerConfig.elasticsearch.tasks_index_name,
      id.toString
    ).script(
      new Script(
        ScriptType.INLINE,
        "painless",
        "ctx._source.status = params.status; ctx._source.finishedAt = params.finishedAt",
        Map[String, AnyRef](
          "status" -> status.toString,
          "finishedAt" -> Instant.now().toString
        ).asJava
      )
    )

    elasticsearchExecutor.update(updateRequest).map(_ => {})
  }
}
