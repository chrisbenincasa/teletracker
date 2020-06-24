package com.teletracker.common.db.dynamo

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.elasticsearch.scraping.EsScrapedItemDoc
import io.circe.Json
import javax.inject.Inject
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  PutItemRequest
}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

class ScrapeItemStore @Inject()(
  teletrackerConfig: TeletrackerConfig,
  dynamoDbAsyncClient: DynamoDbAsyncClient
)(implicit executionContext: ExecutionContext)
    extends DynamoAccess(dynamoDbAsyncClient) {

  def put(item: DynamoScrapedItem): Future[Unit] = {
    dynamoDbAsyncClient
      .putItem(
        PutItemRequest
          .builder()
          .tableName(teletrackerConfig.dynamo.scraped_items.table_name)
          .item(item.toDynamoItem)
          .build()
      )
      .toScala
      .map(_ => {})
  }
}

object DynamoScrapedItem {
  def fromEsScrapedItemDoc(doc: EsScrapedItemDoc): DynamoScrapedItem = {
    DynamoScrapedItem(
      id = doc.id,
      `type` = doc.`type`,
      version = doc.version,
      scrapedItem = doc.raw
    )
  }

  def fromDynamoItem(
    item: java.util.Map[String, AttributeValue]
  ): DynamoScrapedItem = {
    val scMap = item.asScala
    DynamoScrapedItem(
      id = scMap("id").fromAttributeValue[String],
      `type` = scMap("type").fromAttributeValue[String],
      version = scMap("version").fromAttributeValue[Long],
      scrapedItem = scMap("scraped_item").fromAttributeValue[Json]
    )
  }
}

case class DynamoScrapedItem(
  id: String,
  `type`: String,
  version: Long,
  scrapedItem: Json) {

  def toDynamoItem: java.util.Map[String, AttributeValue] =
    Map(
      "id" -> id.toAttributeValue,
      "type" -> `type`.toAttributeValue,
      "version" -> version.toAttributeValue,
      "scraped_item" -> scrapedItem.toAttributeValue
    ).asJava
}
