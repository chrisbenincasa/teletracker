package com.teletracker.common.process.tmdb

import com.teletracker.common.db.model._
import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import shapeless.tag.@@
import shapeless.{:+:, CNil}

object TmdbEntity {
  type Entities = Movie :+: TvShow :+: TmdbPerson :+: CNil
  type Ids =
    (Int @@ MovieId) :+: (Int @@ TvShowId) :+: (Int @@ PersonId) :+: CNil
}

object TmdbEntityProcessor {
  sealed trait ProcessResult
  case class ProcessSuccess(
    tmdbId: String,
    savedThing: ThingLike)
      extends ProcessResult
  case class ProcessFailure(error: Throwable) extends ProcessResult
}
