package com.teletracker.common.db.dynamo.util

import com.teletracker.common.util.Functions._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  QueryRequest
}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

object DynamoQueryUtil {
  def queryLoop(
    dynamo: DynamoDbAsyncClient,
    tableName: String,
    mutateQueryReq: QueryRequest.Builder => QueryRequest.Builder
  )(implicit executionContext: ExecutionContext
  ): Future[List[java.util.Map[String, AttributeValue]]] = {
    def queryLoopInner(
      startKey: Option[java.util.Map[String, AttributeValue]] = None,
      acc: List[java.util.Map[String, AttributeValue]] = Nil
    ): Future[List[java.util.Map[String, AttributeValue]]] = {
      dynamo
        .query(
          QueryRequest
            .builder()
            .tableName(tableName)
            .through(mutateQueryReq)
            .applyOptional(startKey)(_.exclusiveStartKey(_))
            .build()
        )
        .toScala
        .flatMap(response => {
          if (response.lastEvaluatedKey().isEmpty) {
            Future.successful(acc ++ response.items().asScala.toList)
          } else {
            queryLoopInner(
              Some(response.lastEvaluatedKey()),
              acc ++ response.items().asScala.toList
            )
          }
        })
    }

    queryLoopInner()
  }
}
