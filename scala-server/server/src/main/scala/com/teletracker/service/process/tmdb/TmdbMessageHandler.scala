package com.teletracker.service.process.tmdb

import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.model.tmdb.{BelongsToCollection, MovieId}
import com.teletracker.service.process.tmdb.TmdbProcessMessage.{
  ProcessBelongsToCollections,
  ProcessMovie,
  ProcessSearchResults
}
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object TmdbMessageHandler {
  private val logger = LoggerFactory.getLogger(getClass)
}

final class TmdbMessageHandler @Inject()(
  tmdbEntityProcessor: TmdbEntityProcessor,
  tmdbClient: TmdbClient,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext) {
  import TmdbMessageHandler._

  def handle(message: TmdbProcessMessage): Future[Unit] = {
    logger.info(
      s"Handling ${message.action.getClass.getSimpleName} message id = ${message.id}"
    )
    message.action match {
      case ProcessSearchResults(payload) =>
        Future
          .sequence(tmdbEntityProcessor.processSearchResults(payload))
          .map(_ => {})

      case ProcessBelongsToCollections(
          thingId: UUID,
          collection: BelongsToCollection
          ) =>
        tmdbEntityProcessor.processCollection(thingId, collection)

      case ProcessMovie(movieId) =>
        itemExpander
          .expandMovie(movieId)
          .flatMap(tmdbEntityProcessor.handleMovie)
          .map(_ => {})
    }
  }
}
