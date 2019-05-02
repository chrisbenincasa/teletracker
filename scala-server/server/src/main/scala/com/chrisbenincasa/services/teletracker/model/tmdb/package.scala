package com.chrisbenincasa.services.teletracker.model

import shapeless.{:+:, CNil}

package object tmdb {
  type MultiTypeXor = Movie :+: TvShow :+: Person :+: CNil
  type SearchResult = PagedResult[MultiTypeXor]
  type MovieSearchResult = PagedResult[Movie]
}
