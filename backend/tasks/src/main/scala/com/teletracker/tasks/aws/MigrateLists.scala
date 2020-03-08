package com.teletracker.tasks.aws

import com.teletracker.common.db.dynamo.ListsDbAccess
import com.teletracker.common.db.dynamo.model.StoredUserList
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import javax.inject.Inject
import com.teletracker.common.util.Futures._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  BatchWriteItemRequest,
  PutRequest,
  WriteRequest
}
import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

class MigrateLists @Inject()(
  listsDbAccess: ListsDbAccess,
  dynamoDbAsyncClient: DynamoDbAsyncClient)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val allLists = mutable.Buffer[StoredUserList]()
    listsDbAccess
      .processAllLists(list => {
        allLists += list
      })
      .await()

    allLists
      .grouped(10)
      .map(_.toList)
      .foreach(batch => {
        val reqs = Map(
          "teletracker.qa.lists-simple" -> batch
            .map(item => {
              WriteRequest
                .builder()
                .putRequest(
                  PutRequest.builder().item(item.toDynamoItem).build()
                )
                .build()
            })
            .asJavaCollection
        ).asJava

        dynamoDbAsyncClient
          .batchWriteItem(
            BatchWriteItemRequest.builder().requestItems(reqs).build()
          )
          .toScala
          .await()
      })
  }
}
