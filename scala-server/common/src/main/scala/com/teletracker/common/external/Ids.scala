package com.teletracker.common.external

import shapeless.{:+:, CNil}
import shapeless.tag.@@

object Ids {
  type ExternalIds =
    (String @@ MovieId) :+: (String @@ TvShowId) :+: (String @@ PersonId) :+: CNil
}

trait MovieId
trait TvShowId
trait PersonId
