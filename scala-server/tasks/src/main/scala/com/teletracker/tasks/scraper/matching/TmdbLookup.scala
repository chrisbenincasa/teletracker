package com.teletracker.tasks.scraper.matching

import com.teletracker.common.db.model.ThingRaw
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessSuccess
}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.scraper.{
  IngestJobArgsLike,
  MatchResult,
  ScrapedItem,
  ScrapedItemMatcher
}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

class TmdbLookup(
  tmdbClient: TmdbClient,
  tmdbProcessor: TmdbEntityProcessor
)(implicit executionContext: ExecutionContext)
    extends MatchMode {

  private val logger = LoggerFactory.getLogger(getClass)

  override def lookup[T <: ScrapedItem](
    items: List[T],
    args: IngestJobArgsLike
  ): Future[(List[MatchResult[T]], List[T])] = {
    SequentialFutures
      .serialize(items)(lookupSingle(_, args))
      .map(_.flatten)
      .map(results => {
        val (x, y) = results.partition(_.isLeft)
        x.flatMap(_.left.toOption) -> y.flatMap(_.right.toOption)
      })
  }

  private def lookupSingle[T <: ScrapedItem](
    item: T,
    args: IngestJobArgsLike
  ): Future[Option[Either[MatchResult[T], T]]] = {
    val search = if (item.isMovie) {
      tmdbClient.searchMovies(item.title).map(_.results.map(Left(_)))
    } else if (item.isTvShow) {
      tmdbClient.searchTv(item.title).map(_.results.map(Right(_)))
    } else {
      Future.failed(new IllegalArgumentException)
    }

    val matcher = new ScrapedItemMatcher

    search
      .flatMap(results => {
        results
          .find(matcher.findMatch(_, item, args.titleMatchThreshold)) match {
          case Some(foundMatch) =>
            tmdbProcessor.handleWatchable(foundMatch).flatMap {
              case ProcessSuccess(_, thing: ThingRaw) =>
                logger.info(
                  s"Saved ${item.title} with thing ID = ${thing.id}"
                )

                Future.successful(
                  Some(Left(MatchResult(item, thing.id, thing.name)))
                )

              case ProcessSuccess(_, _) =>
                logger.error("Unexpected result")
                Future.successful(None)

              case ProcessFailure(error) =>
                logger.error("Error handling movie", error)
                Future.successful(None)
            }

          case None =>
            Future.successful(Some(Right(item)))
        }
      })
  }
}
