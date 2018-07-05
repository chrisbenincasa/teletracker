package com.chrisbenincasa.services.teletracker.model

import shapeless.{:+:, CNil}

package object tmdb {
  type SearchResult = PagedResult[Movie :+: TvShow :+: Person :+: CNil]
}
