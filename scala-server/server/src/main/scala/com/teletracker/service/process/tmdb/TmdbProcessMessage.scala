package com.teletracker.service.process.tmdb

import com.teletracker.service.model.tmdb.{
  BelongsToCollection,
  MovieId,
  MultiTypeXor
}
import com.teletracker.service.process.Message
import com.teletracker.service.process.tmdb.TmdbProcessMessage.MessageActionType
import shapeless.tag.@@
import java.util.UUID

object TmdbProcessMessage {
  sealed trait MessageActionType
  case class ProcessSearchResults(payload: List[MultiTypeXor])
      extends MessageActionType

  case class ProcessBelongsToCollections(
    thingId: Int,
    collection: BelongsToCollection)
      extends MessageActionType

  case class ProcessMovie(movieId: Int @@ MovieId) extends MessageActionType

  def make(action: MessageActionType): TmdbProcessMessage = {
    TmdbProcessMessage(UUID.randomUUID().toString, action)
  }
}

final case class TmdbProcessMessage private (
  id: String,
  action: MessageActionType)
    extends Message
