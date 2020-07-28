package com.teletracker.tasks.scraper.loaders

import com.teletracker.common.db.model.SupportedNetwork
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Decoder
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

case class UriAvailabilityItemLoaderArgs(
  override val supportedNetworks: Set[SupportedNetwork],
  location: URI)
    extends AvailabilityItemLoaderArgs

class UriAvailabilityItemLoaderFactory @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext) {
  def make[T: Decoder]: UriAvailabilityItemLoader[T] =
    new UriAvailabilityItemLoader[T](sourceRetriever)
}

class UriAvailabilityItemLoader[T: Decoder](
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends AvailabilityItemLoader[T, UriAvailabilityItemLoaderArgs] {
  private val logger = LoggerFactory.getLogger(getClass)

  override protected def loadImpl(
    args: UriAvailabilityItemLoaderArgs
  ): Future[List[T]] = {
    val source = sourceRetriever.getSource(args.location, consultCache = true)
    try {
      // TODO Don't block calling thread
      val items = new IngestJobParser()
        .stream[T](source.getLines())
        .flatMap {
          case Left(value) =>
            logger.warn(s"Could not parse line", value)
            None
          case Right(value) => Some(value)
        }
        .toList

      Future.successful(items)
    } finally {
      source.close()
    }
  }
}
