package com.teletracker.tasks.scraper

import com.teletracker.common.aws.sqs.SqsQueue
import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.pubsub.ScrapeItemIngestMessage
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.loaders.{
  CrawlAvailabilityItemLoaderArgs,
  CrawlAvailabilityItemLoaderFactory
}
import io.circe.Json
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.ExecutionContext

@JsonCodec
@GenArgParser
case class ReplayScrapeToQueueArgs(
  spider: String,
  version: Long,
  itemType: String)

class ReplayScrapeToQueue @Inject()(
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory,
  queue: SqsQueue[ScrapeItemIngestMessage]
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[ReplayScrapeToQueueArgs] {
  override protected def runInternal(): Unit = {
    crawlAvailabilityItemLoaderFactory
      .make[Json]
      .load(
        CrawlAvailabilityItemLoaderArgs(
          Set.empty,
          new CrawlerName(args.spider),
          Some(args.version)
        )
      )
      .flatMap(items => {
        AsyncStream
          .fromIterable(items)
          .grouped(10)
          .foreachF(items => {
            val messages = items
              .map(item => {
                ScrapeItemIngestMessage(
                  `type` = args.itemType,
                  version = args.version,
                  item = item
                )
              })
              .toList

            queue.batchQueue(messages).map(_ => {})
          })
      })
      .await()
  }
}
