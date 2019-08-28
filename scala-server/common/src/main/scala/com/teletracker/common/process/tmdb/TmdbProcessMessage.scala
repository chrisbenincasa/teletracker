package com.teletracker.common.process.tmdb

import com.teletracker.common.model.tmdb.{
  BelongsToCollection,
  Movie,
  MovieId,
  MultiTypeXor,
  PersonId,
  TvShow,
  TvShowId
}
import com.teletracker.common.process.Message
import com.teletracker.common.process.tmdb.TmdbProcessMessage.MessageActionType
import shapeless.{:+:, CNil}
import shapeless.tag.@@
import java.util.UUID

object TmdbProcessMessage {
  sealed trait MessageActionType
  case class ProcessSearchResults(payload: List[Movie :+: TvShow :+: CNil])
      extends MessageActionType

  case class ProcessBelongsToCollections(
    thingId: UUID,
    collection: BelongsToCollection)
      extends MessageActionType

  case class ProcessTvShow(showId: Int @@ TvShowId) extends MessageActionType

  case class ProcessMovie(movieId: Int @@ MovieId) extends MessageActionType

  case class ProcessPerson(personId: Int @@ PersonId) extends MessageActionType

  def make(action: MessageActionType): TmdbProcessMessage = {
    TmdbProcessMessage(UUID.randomUUID().toString, action)
  }
}

final case class TmdbProcessMessage private (
  id: String,
  action: MessageActionType)
    extends Message
