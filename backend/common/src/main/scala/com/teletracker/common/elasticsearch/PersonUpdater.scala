package com.teletracker.common.elasticsearch

import com.teletracker.common.config.TeletrackerConfig
import io.circe.syntax._
import javax.inject.Inject
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import scala.concurrent.{ExecutionContext, Future}

class PersonUpdater @Inject()(
  elasticsearchExecutor: ElasticsearchExecutor,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends ElasticsearchAccess {

  def insert(person: EsPerson): Future[IndexResponse] = {
    val indexRequest =
      new IndexRequest(teletrackerConfig.elasticsearch.people_index_name)
        .create(true)
        .id(person.id.toString)
        .source(person.asJson.noSpaces, XContentType.JSON)

    elasticsearchExecutor.index(indexRequest)
  }

  def update(person: EsPerson): Future[UpdateResponse] = {
    val updateRequest = new UpdateRequest(
      teletrackerConfig.elasticsearch.people_index_name,
      person.id.toString
    ).doc(
      person.asJson.noSpaces,
      XContentType.JSON
    )

    elasticsearchExecutor.update(updateRequest)
  }
}
