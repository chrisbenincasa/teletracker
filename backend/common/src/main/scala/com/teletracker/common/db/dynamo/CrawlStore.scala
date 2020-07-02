package com.teletracker.common.db.dynamo

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.db.dynamo.util.syntax._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import shapeless.tag.@@
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  ComparisonOperator,
  Condition,
  GetItemRequest,
  QueryRequest
}
import java.net.URI
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._

object CrawlStore {
  final val HboCatalog = new CrawlerName("hbo_go_catalog")
}

class CrawlerName(val name: String) extends AnyVal {
  override def toString: String = name
}

class CrawlStore @Inject()(
  teletrackerConfig: ReloadableConfig[TeletrackerConfig],
  dynamo: DynamoDbAsyncClient
)(implicit executionContext: ExecutionContext) {
  def getCrawlAtVersion(
    crawler: CrawlerName,
    version: Long
  ): Future[Option[HistoricalCrawl]] = {
    dynamo
      .getItem(
        GetItemRequest
          .builder()
          .tableName(teletrackerConfig.currentValue().dynamo.crawls.table_name)
          .key(
            Map(
              "spider" -> crawler.name.toAttributeValue,
              "version" -> version.toAttributeValue
            ).asJava
          )
          .build()
      )
      .toScala
      .map(response => {
        Option(response.item())
          .map(_.asScala.toMap)
          .map(HistoricalCrawl.fromDynamoRow)
      })
  }

  def getLatestCrawl(crawler: CrawlerName): Future[Option[HistoricalCrawl]] = {
    dynamo
      .query(
        QueryRequest
          .builder()
          .tableName(teletrackerConfig.currentValue().dynamo.crawls.table_name)
          .keyConditions(
            Map(
              "spider" -> Condition
                .builder()
                .comparisonOperator(ComparisonOperator.EQ)
                .attributeValueList(crawler.name.toAttributeValue)
                .build()
            ).asJava
          )
          .filterExpression("attribute_exists(#tc)")
          .expressionAttributeNames(
            Map(
              "#tc" -> "time_closed"
            ).asJava
          )
          .scanIndexForward(false)
          .limit(1)
          .build()
      )
      .toScala
      .map(response => {
        response
          .items()
          .asScala
          .headOption
          .map(_.asScala.toMap)
          .map(HistoricalCrawl.fromDynamoRow)
      })
  }
}

object HistoricalCrawl {
  def fromDynamoRow(m: Map[String, AttributeValue]): HistoricalCrawl = {
    HistoricalCrawl(
      spider = m("spider").fromAttributeValue[String],
      version = m("version").fromAttributeValue[Long],
      timeOpened = m("time_opened").fromAttributeValue[Instant @@ EpochSeconds],
      timeClosed =
        m.get("time_closed").map(_.fromAttributeValue[Instant @@ EpochSeconds]),
      totalItemsScraped =
        m.get("total_items_scraped").map(_.fromAttributeValue[Long]),
      metadata =
        m.get("metadata").map(_.fromAttributeValue[HistoricalCrawlMetadata])
    )
  }
}

@JsonCodec
case class HistoricalCrawl(
  spider: String,
  version: Long,
  timeOpened: Instant,
  timeClosed: Option[Instant],
  totalItemsScraped: Option[Long],
  metadata: Option[HistoricalCrawlMetadata]) {
  private lazy val outputsMap = metadata
    .flatMap(_.outputs)
    .map(_.fold(Map.empty[String, HistoricalCrawlOutput])(_ ++ _))
    .getOrElse(Map.empty)

  def getOutputWithScheme(
    scheme: String
  ): Option[(URI, HistoricalCrawlOutput)] = {
    if (outputsMap.isEmpty) {
      None
    } else {
      outputsMap.toStream.flatMap {
        case (str, output) =>
          val uri = URI.create(str)
          if (uri.getScheme == scheme) {
            Some(uri -> output)
          } else {
            None
          }
      }.headOption
    }
  }
}

@JsonCodec
case class HistoricalCrawlMetadata(
  outputs: Option[List[Map[String, HistoricalCrawlOutput]]])

@JsonCodec
case class HistoricalCrawlOutput(format: String)
