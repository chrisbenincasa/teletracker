package com.teletracker.tasks.scraper.loaders

import com.teletracker.common.db.dynamo.{CrawlStore, CrawlerName}
import com.teletracker.common.db.model.SupportedNetwork
import com.teletracker.common.model.scraping.ScrapedItem
import io.circe.Decoder
import javax.inject.Inject
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

case class CrawlAvailabilityItemLoaderArgs(
  override val supportedNetworks: Set[SupportedNetwork],
  crawlerName: CrawlerName,
  version: Option[Long])
    extends AvailabilityItemLoaderArgs

class CrawlAvailabilityItemLoaderFactory @Inject()(
  crawlStore: CrawlStore,
  s3AvailabilityItemLoaderFactory: UriAvailabilityItemLoaderFactory
)(implicit executionContext: ExecutionContext) {
  def make[T <: ScrapedItem: Decoder]: CrawlAvailabilityItemLoader[T] =
    new CrawlAvailabilityItemLoader[T](
      crawlStore,
      s3AvailabilityItemLoaderFactory.make[T]
    )
}

class CrawlAvailabilityItemLoader[T <: ScrapedItem: Decoder](
  crawlStore: CrawlStore,
  s3AvailabilityItemLoader: UriAvailabilityItemLoader[T]
)(implicit executionContext: ExecutionContext)
    extends AvailabilityItemLoader[T, CrawlAvailabilityItemLoaderArgs] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def loadImpl(
    args: CrawlAvailabilityItemLoaderArgs
  ): Future[List[T]] = {
    (args.version match {
      case Some(value) =>
        crawlStore.getCrawlAtVersion(args.crawlerName, value)
      case None =>
        crawlStore
          .getLatestCrawl(args.crawlerName)
    }).map {
        case Some(value) =>
          logger.info(
            s"Found crawl for ${args.crawlerName} with version ${value.version}"
          )

          value.getOutputWithScheme("s3") match {
            case Some((loc, _)) =>
              logger.info(s"Found s3 output for crawl at: $loc")
              loc
            case None =>
              throw new IllegalArgumentException(
                s"Could not find s3 output for latest crawler version: ${value.spider} version=${value.version}"
              )
          }
        case None =>
          throw new IllegalArgumentException(
            s"Could not find ${if (args.version.isEmpty) "latest"
            else args.version.get} version for crawler: ${args.crawlerName}"
          )
      }
      .flatMap(loc => {
        s3AvailabilityItemLoader
          .load(UriAvailabilityItemLoaderArgs(args.supportedNetworks, loc))
      })
  }
}
