package com.teletracker.common.process.tmdb

import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.BelongsToCollection
import com.teletracker.common.process.tmdb.TmdbProcessMessage.{
  ProcessBelongsToCollections,
  ProcessMovie,
  ProcessPerson,
  ProcessSearchResults,
  ProcessTvShow
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
      s"Handling ${message.action.getClass.getSimpleName} message id = ${message.id} with action: ${message.action}"
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
          .expandMovie(movieId, Nil)
          .flatMap(tmdbEntityProcessor.handleMovie)
          .map(_ => {})

      case ProcessTvShow(showId) =>
        itemExpander
          .expandTvShow(showId, Nil)
          .flatMap(tmdbEntityProcessor.handleShow(_, handleSeasons = false))
          .map(_ => {})

      case ProcessPerson(personId) =>
        itemExpander
          .expandPerson(personId)
          .flatMap(tmdbEntityProcessor.handlePerson)
          .map(_ => {})
    }
  }
}
