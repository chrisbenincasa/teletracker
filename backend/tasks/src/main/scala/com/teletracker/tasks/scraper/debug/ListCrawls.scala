package com.teletracker.tasks.scraper.debug

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.dynamo.util.DynamoQueryUtil
import com.teletracker.common.db.dynamo.util.syntax._
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.time.Instant
import io.circe.syntax._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class ListCrawls @Inject()(
  dynamo: DynamoDbAsyncClient,
  crawlStore: CrawlStore
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val spiderName = rawArgs.valueOrThrow[String]("spider")

//    DynamoQueryUtil
//      .queryLoop(
//        dynamo,
//        "teletracker.qa.crawls",
//        _.keyConditionExpression("#t = :v1")
//          .expressionAttributeNames(
//            Map(
//              "#t" -> "spider"
//            ).asJava
//          )
//          .expressionAttributeValues(
//            Map(
//              ":v1" -> spiderName.toAttributeValue
//            ).asJava
//          )
//          .scanIndexForward(false)
//      )
//      .map(_.map(_.asScala).foreach(row => {
//        val version = row("version").fromAttributeValue[Long]
//        val instant = Instant.ofEpochSecond(version)
//        println(s"${spiderName}: $version, at $instant")
//      }))
//      .await()

    crawlStore
      .getLatestCrawl(new CrawlerName(spiderName))
      .await()
      .map(_.asJson.spaces2)
      .foreach(println(_))
  }
}
