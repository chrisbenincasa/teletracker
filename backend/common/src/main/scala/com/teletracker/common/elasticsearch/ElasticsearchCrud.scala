package com.teletracker.common.elasticsearch

import com.teletracker.common.elasticsearch.model.UpdateableEsItem
import com.teletracker.common.util.{AsyncStream, HasId}
import io.circe.{Codec, Encoder}
import io.circe.syntax._
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.{DeleteRequest, DeleteResponse}
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.update.{UpdateRequest, UpdateResponse}
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.reindex.{
  BulkByScrollResponse,
  UpdateByQueryRequest
}
import org.elasticsearch.script.Script
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

abstract class ElasticsearchCrud[Id, T: Codec: ClassTag](
  implicit hasId: HasId.Aux[T, Id],
  executionContext: ExecutionContext)
    extends ElasticsearchAccess {
  protected def indexName: String
  protected def elasticsearchExecutor: ElasticsearchExecutor

  def lookup(id: Id): Future[Option[T]] = {
    elasticsearchExecutor
      .get(
        new GetRequest(indexName, hasId.asString(id))
      )
      .map(response => {
        decodeSourceString[T](response.getSourceAsString)
      })
  }

  def index(item: T): Future[IndexResponse] = {
    elasticsearchExecutor.index(
      new IndexRequest(indexName)
        .create(true)
        .id(hasId.idString(item))
        .jsonSource(item)
    )
  }

  def indexBatch(items: List[T]): Future[Unit] = {
    AsyncStream
      .fromStream(items.grouped(50).toStream)
      .foreachF(batch => {
        val bulkRequest = new BulkRequest()
        batch.foreach(
          insert =>
            bulkRequest.add(
              new IndexRequest(indexName)
                .id(hasId.idString(insert))
                .create(true)
                .jsonSource(insert)
            )
        )

        elasticsearchExecutor.bulk(bulkRequest).map(_ => {})
      })
  }

  def update(item: T): Future[UpdateResponse] = {
    elasticsearchExecutor.update(
      new UpdateRequest(indexName, hasId.idString(item))
        .jsonDoc(item, removeNulls = true)
    )
  }

  def delete(id: Id): Future[DeleteResponse] = {
    elasticsearchExecutor.delete(
      new DeleteRequest(indexName, hasId.asString(id))
    )
  }

  def updateByQuery(
    query: QueryBuilder,
    script: Script
  ): Future[BulkByScrollResponse] = {
    elasticsearchExecutor.updateByQuery(
      new UpdateByQueryRequest(indexName)
        .setRequestsPerSecond(25)
        .setQuery(query)
        .setScript(script)
    )
  }

  def upsertBatch(items: List[T]): Future[Unit] = {
    AsyncStream
      .fromStream(items.grouped(25).toStream)
      .foreachF(batch => {
        val bulkRequest = new BulkRequest()
        batch.foreach(
          insert =>
            bulkRequest.add(
              new UpdateRequest(indexName, hasId.idString(insert))
                .id(hasId.idString(insert))
                .jsonDoc(
                  insert.asJson,
                  removeNulls = true
                )
                .upsert(
                  insert.asJson.deepDropNullValues.noSpaces,
                  XContentType.JSON
                )
            )
        )

        elasticsearchExecutor.bulk(bulkRequest).map(_ => {})
      })
  }

  def upsertBatchWithFallback[U: Encoder](
    items: List[(U, T)]
  )(implicit updateableEsItem: UpdateableEsItem.Aux[T, U]
  ): Future[Unit] = {
    AsyncStream
      .fromStream(items.grouped(25).toStream)
      .foreachF(batch => {
        val bulkRequest = new BulkRequest()
        batch.foreach {
          case (update, insert) =>
            bulkRequest.add(
              new UpdateRequest(indexName, hasId.idString(insert))
                .id(hasId.idString(insert))
                .doc(
                  update.asJson.deepDropNullValues.noSpaces,
                  XContentType.JSON
                )
                .upsert(
                  insert.asJson.deepDropNullValues.noSpaces,
                  XContentType.JSON
                )
            )
        }

        elasticsearchExecutor.bulk(bulkRequest).map(_ => {})
      })
  }
}
