package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.model.EsPerson
import com.teletracker.common.util.Functions._
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.{
  ToXContent,
  XContentHelper,
  XContentType
}
import scala.concurrent.{ExecutionContext, Future}

class PersonUpdater @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def insert(person: EsPerson): Future[IndexResponse] = {
    val indexRequest = getIndexRequest(person)
    elasticsearchExecutor.index(indexRequest)
  }

  def update(person: EsPerson): Future[UpdateResponse] =
    update(person, refresh = false)

  def update(
    person: EsPerson,
    refresh: Boolean
  ): Future[UpdateResponse] = {
    elasticsearchExecutor.update(getUpdateRequest(person, refresh))
  }

  def getIndexJson(person: EsPerson) = {
    person.asJson.noSpaces
  }

  def getUpdateJson(
    person: EsPerson,
    refresh: Boolean = false
  ): String = {
    XContentHelper
      .toXContent(
        getUpdateRequest(person, refresh),
        XContentType.JSON,
        ToXContent.EMPTY_PARAMS,
        true
      )
      .utf8ToString
  }

  private def getIndexRequest(person: EsPerson) = {
    new IndexRequest(teletrackerConfig.elasticsearch.people_index_name)
      .create(true)
      .id(person.id.toString)
      .source(person.asJson.noSpaces, XContentType.JSON)
  }

  private def getUpdateRequest(
    person: EsPerson,
    refresh: Boolean
  ) = {
    new UpdateRequest(
      teletrackerConfig.elasticsearch.people_index_name,
      person.id.toString
    ).doc(
        person.asJson.noSpaces,
        XContentType.JSON
      )
      .applyIf(refresh)(_.setRefreshPolicy(RefreshPolicy.IMMEDIATE))
  }
}
