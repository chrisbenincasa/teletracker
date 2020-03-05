package com.teletracker.common.db.dynamo

import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import org.reactivestreams.{Subscriber, Subscription}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._
import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

object ListsDbAccess {
  final private val ListTable = "teletracker.qa.lists"
}

class ListsDbAccess @Inject()(
  dynamo: DynamoDbAsyncClient
)(implicit executionContext: ExecutionContext) {
  import ListsDbAccess._

  def saveList(list: StoredUserList): Future[StoredUserList] = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(ListTable)
          .item(list.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => list)
  }

  def deleteList(
    listId: UUID,
    userId: String
  ): Future[Int] = {
    dynamo
      .updateItem(
        UpdateItemRequest
          .builder()
          .tableName(ListTable)
          .attributeUpdates(
            Map(
              "deletedAt" -> AttributeValueUpdate
                .builder()
                .action(AttributeAction.PUT)
                .value(OffsetDateTime.now().toString.toAttributeValue)
                .build()
            ).asJava
          )
          .key(StoredUserList.primaryKey(listId, userId))
          .build()
      )
      .toScala
      .map(_ => 1)
      .recover {
        case _: ResourceNotFoundException => 0
      }
  }

  def getList(
    userId: String,
    listId: UUID
  ): Future[Option[StoredUserList]] = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(ListTable)
          .key(StoredUserList.primaryKey(listId, userId))
          .build()
      )
      .toScala
      .map(response => {
        if (response.item().isEmpty) {
          None
        } else {
          Some(StoredUserList.fromRow(response.item()))
            .filter(_.deletedAt.isEmpty)
        }
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getDefaultList(userId: String) = {
    dynamo
      .query(
        QueryRequest
          .builder()
          .tableName(ListTable)
          .indexName("userId-id-inverted-index")
          .keyConditionExpression("userId = :v1")
          .filterExpression("isDefault = :v2")
          .expressionAttributeValues(
            Map(
              ":v1" -> userId.toAttributeValue,
              ":v2" -> true.toAttributeValue
            ).asJava
          )
          .limit(1)
          .build()
      )
      .toScala
      .map(response => {
        response.items.asScala.headOption.map(StoredUserList.fromRow)
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getListByLegacyId(
    userId: String,
    listId: Int
  ): Future[Option[StoredUserList]] = {
    dynamo
      .query(
        QueryRequest
          .builder()
          .tableName(ListTable)
          .indexName("legacyId-userId-index-copy")
          .keyConditionExpression("#id = :legacy_id and userId = :user_id")
          .expressionAttributeNames(Map("#id" -> "legacyId").asJava)
          .expressionAttributeValues(
            Map(
              ":legacy_id" -> listId.toAttributeValue,
              ":user_id" -> userId.toAttributeValue
            ).asJava
          )
          .limit(1)
          .build()
      )
      .toScala
      .map(response => {
        response.items.asScala.headOption.map(StoredUserList.fromRow)
      })
      .recover {
        case _: ResourceNotFoundException => None
      }
  }

  def getAllListsForUser(userId: String): Future[List[StoredUserList]] = {
    def getAllListsForUserInner(
      startKey: Option[java.util.Map[String, AttributeValue]] = None,
      acc: List[java.util.Map[String, AttributeValue]] = Nil
    ): Future[List[java.util.Map[String, AttributeValue]]] = {
      dynamo
        .query(
          QueryRequest
            .builder()
            .tableName(ListTable)
            .indexName("userId-id-inverted-index")
            .keyConditionExpression("userId = :v1")
            .filterExpression("attribute_not_exists(deletedAt)")
            .expressionAttributeValues(
              Map(
                ":v1" -> userId.toAttributeValue
              ).asJava
            )
            .applyOptional(startKey)(_.exclusiveStartKey(_))
            .build()
        )
        .toScala
        .flatMap(response => {
          if (response.lastEvaluatedKey().isEmpty) {
            Future.successful(acc ++ response.items().asScala.toList)
          } else {
            getAllListsForUserInner(
              Some(response.lastEvaluatedKey()),
              acc ++ response.items().asScala.toList
            )
          }
        })
    }

    getAllListsForUserInner().map(lists => {
      lists.map(StoredUserList.fromRow)
    })
  }

  def processAllLists(onItem: StoredUserList => Unit): Future[Unit] = {
    processLists(list => {
      onItem(list)
      true
    })
  }

  def processLists(onItem: StoredUserList => Boolean): Future[Unit] = {
    val promise = Promise[Unit]()

    val publisher = dynamo
      .scanPaginator(ScanRequest.builder().tableName(ListTable).build())
      .items()

    publisher
      .subscribe(new Subscriber[java.util.Map[String, AttributeValue]] {
        private val subscriptions = mutable.ListBuffer[Subscription]()

        override def onSubscribe(s: Subscription): Unit = {
          subscriptions += s
          s.request(1)
        }

        override def onNext(t: java.util.Map[String, AttributeValue]): Unit = {
          if (!onItem(StoredUserList.fromRow(t))) {
            subscriptions.foreach(_.cancel())
          } else {
            subscriptions.foreach(_.request(1))
          }
        }

        override def onError(t: Throwable): Unit =
          promise.failure(t)

        override def onComplete(): Unit = promise.success(Unit)
      })

    promise.future
  }
}
