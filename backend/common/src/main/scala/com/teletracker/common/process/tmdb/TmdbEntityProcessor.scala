package com.teletracker.common.process.tmdb

import com.teletracker.common.model.tmdb.{Person => TmdbPerson, _}
import shapeless.tag.@@
import shapeless.{:+:, CNil}

object TmdbEntity {
  type Entities = Movie :+: TvShow :+: TmdbPerson :+: CNil
  type Ids =
    (Int @@ MovieId) :+: (Int @@ TvShowId) :+: (Int @@ PersonId) :+: CNil
}
