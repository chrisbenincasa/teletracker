package com.teletracker.common.db.dynamo

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.config.core.api.ReloadableConfig
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.util.{AsyncToken, Cancellable, FutureToken}
import io.circe.generic.JsonCodec
import javax.inject.Inject
import com.teletracker.common.util.Functions._
import shapeless.tag.@@
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeAction,
  AttributeValue,
  AttributeValueUpdate,
  ComparisonOperator,
  Condition,
  ConditionalOperator,
  ExpectedAttributeValue,
  GetItemRequest,
  PutItemRequest,
  QueryRequest,
  UpdateItemRequest
}
import java.net.URI
import java.time.Instant
import java.util.concurrent.{
  ScheduledExecutorService,
  ScheduledFuture,
  TimeUnit
}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._

object CrawlStore {
  final val HboCatalog = new CrawlerName("hbo_go_catalog")
  final val HboChanges = new CrawlerName("hbo_changes")
  final val HboMaxCatalog = new CrawlerName("hbo_max_authenticated")
  final val NetflixCatalog = new CrawlerName("netflix")
  final val NetflixOriginalsArriving = new CrawlerName(
    "netflix_originals_arriving"
  )
  final val HuluCatalog = new CrawlerName("hulu")
  final val HuluChanges = new CrawlerName("hulu_changes")
  final val DisneyPlusCatalog = new CrawlerName("disneyplus")
  final val AmazonCatalog = new CrawlerName("amazon")
}

class CrawlerName(val name: String) extends AnyVal {
  override def toString: String = name
}

class CrawlStore @Inject()(
  teletrackerConfig: ReloadableConfig[TeletrackerConfig],
  dynamo: DynamoDbAsyncClient,
  @SingleThreaded scheduledExecutorService: ScheduledExecutorService
)(implicit executionContext: ExecutionContext) {
  def closeCrawl(
    crawlerName: CrawlerName,
    version: Long,
    totalItemsCrawled: Option[Int]
  ): Future[Unit] = {
    dynamo
      .updateItem(
        UpdateItemRequest
          .builder()
          .tableName(teletrackerConfig.currentValue().dynamo.crawls.table_name)
          .key(
            Map(
              "spider" -> crawlerName.name.toAttributeValue,
              "version" -> version.toAttributeValue
            ).asJava
          )
          .attributeUpdates(
            Map(
              "time_closed" -> AttributeValueUpdate
                .builder()
                .action(AttributeAction.PUT)
                .value(Instant.now().getEpochSecond.toAttributeValue)
                .build()
            ).applyOptional(totalItemsCrawled)(
                (m, total) =>
                  m ++ Map(
                    "total_items_crawled" -> AttributeValueUpdate
                      .builder()
                      .action(AttributeAction.PUT)
                      .value(total.toAttributeValue)
                      .build()
                  )
              )
              .asJava
          )
          .expected(
            Map(
              "time_closed" -> ExpectedAttributeValue
                .builder()
                .exists(false)
                .build()
            ).asJava
          )
          .build()
      )
      .toScala
      .map(_ => {})
  }

  def saveCrawl(crawl: HistoricalCrawl): Future[Unit] = {
    dynamo
      .putItem(
        PutItemRequest
          .builder()
          .tableName(teletrackerConfig.currentValue().dynamo.crawls.table_name)
          .item(
            crawl.toDynamoItem
          )
          .build()
      )
      .toScala
      .map(_ => {})
  }

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

  def getInProgressCrawls(
    crawler: CrawlerName
  ): Future[List[HistoricalCrawl]] = {
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
          .filterExpression("attribute_not_exists(#tc)")
          .expressionAttributeNames(
            Map(
              "#tc" -> "time_closed"
            ).asJava
          )
          .scanIndexForward(false)
          .build()
      )
      .toScala
      .map(response => {
        response
          .items()
          .asScala
          .toList
          .map(_.asScala.toMap)
          .map(HistoricalCrawl.fromDynamoRow)
      })
  }

  def waitForActiveCrawlCompletion(
    crawlerName: CrawlerName,
    version: Option[Long],
    frequency: FiniteDuration = 10 seconds
  ): Future[InProgressCrawlToken] = {
    val crawlFut = version match {
      case Some(value) =>
        getCrawlAtVersion(crawlerName, value)
      case None =>
        getLatestCrawl(crawlerName)
    }

    crawlFut.map {
      case Some(value) if value.timeClosed.isDefined =>
        InProgressCrawlToken.successful
      case Some(foundCrawl) =>
        val p = Promise[Unit]()
        val fut = scheduledExecutorService.schedule(
          new Runnable {
            override def run(): Unit = {
              getCrawlAtVersion(crawlerName, foundCrawl.version).map {
                case Some(value) if value.timeClosed.isDefined =>
                  p.trySuccess(Unit)
                case Some(_) =>
                case None =>
                  p.tryFailure(
                    new RuntimeException(
                      s"Crawler ${crawlerName} at version ${version} doesn't exist anymore}"
                    )
                  )
              }
            }
          },
          frequency.toMillis,
          TimeUnit.MILLISECONDS
        )

        new InProgressCrawlToken(p.future, Some(fut))

      case None =>
        throw new IllegalArgumentException(
          s"No crawler with name: ${crawlerName} found at verison ${version}"
        )
    }
  }
}

object InProgressCrawlToken {
  def successful: InProgressCrawlToken =
    new InProgressCrawlToken(Future.unit, None)
}

class InProgressCrawlToken(
  awaiter: Future[Unit],
  underlying: Option[ScheduledFuture[_]])
    extends FutureToken[Unit](awaiter)
    with Cancellable {
  override def cancel(): Unit = {
    underlying.foreach(_.cancel(true))
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
        m.get("metadata").map(_.fromAttributeValue[HistoricalCrawlMetadata]),
      numOpenSpiders = m.get("num_open_spiders").map(_.fromAttributeValue[Int]),
      isDistributed = m.get("is_distributed").map(_.fromAttributeValue[Boolean])
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
  metadata: Option[HistoricalCrawlMetadata],
  numOpenSpiders: Option[Int],
  isDistributed: Option[Boolean]) {
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

  def toDynamoItem: java.util.Map[String, AttributeValue] = {
    (Map(
      "spider" -> spider.toAttributeValue,
      "version" -> version.toAttributeValue,
      "time_opened" -> timeOpened.toAttributeValueTagged[EpochSeconds]
    ) ++ Map(
      "time_closed" -> timeClosed.map(_.toAttributeValueTagged[EpochSeconds]),
      "total_items_scraped" -> totalItemsScraped.map(_.toAttributeValue),
      "metadata" -> metadata.map(_.toAttributeValue),
      "num_open_spiders" -> numOpenSpiders.map(_.toAttributeValue),
      "is_distributed" -> isDistributed.map(_.toAttributeValue)
    ).collect {
      case (k, Some(v)) => k -> v
    }).asJava
  }
}

@JsonCodec
case class HistoricalCrawlMetadata(
  outputs: Option[List[Map[String, HistoricalCrawlOutput]]])

@JsonCodec
case class HistoricalCrawlOutput(format: String)
