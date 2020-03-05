package com.teletracker.tasks.aws

import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import javax.inject.Inject
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  BatchWriteItemRequest,
  PutRequest,
  ScanRequest,
  WriteRequest
}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class MigrateDynamoTable @Inject()(
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val fromRegion = args.valueOrThrow[String]("fromRegion")
    val toRegion = args.valueOrThrow[String]("toRegion")
    val table = args.valueOrThrow[String]("tableName")

    val toClient = DynamoDbClient.builder().region(Region.of(toRegion)).build()

    AsyncStream
      .fromStream(
        getStream(fromRegion, table)
      )
      .grouped(10)
      .foreach(itemBatch => {
        val writeRequests = itemBatch
          .map(
            item =>
              WriteRequest
                .builder()
                .putRequest(PutRequest.builder().item(item).build())
                .build()
          )
          .asJavaCollection

        toClient.batchWriteItem(
          BatchWriteItemRequest
            .builder()
            .requestItems(Map(table -> writeRequests).asJava)
            .build()
        )

        logger.info(s"Inserted ${itemBatch.size} items")
      })
      .await()
  }

  private def getStream(
    fromRegion: String,
    table: String
  ): Stream[java.util.Map[String, AttributeValue]] = {
    import scala.compat.java8.StreamConverters._

    val fromClient =
      DynamoDbClient.builder().region(Region.of(fromRegion)).build()

    fromClient
      .scanPaginator(ScanRequest.builder().tableName(table).build())
      .items()
      .stream()
      .toScala
  }
}
