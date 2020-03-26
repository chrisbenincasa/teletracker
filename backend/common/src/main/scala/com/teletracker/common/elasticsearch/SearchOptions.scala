package com.teletracker.common.elasticsearch

import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.db.{Bookmark, SearchRankingMode}
import com.teletracker.common.util.OpenDateRange

case class SearchOptions(
  rankingMode: SearchRankingMode,
  thingTypeFilter: Option[Set[ItemType]],
  limit: Int = 20,
  bookmark: Option[Bookmark] = None,
  genres: Option[Set[StoredGenre]] = None,
  networks: Option[Set[StoredNetwork]] = None,
  releaseYear: Option[OpenDateRange] = None,
  peopleCreditSearch: Option[PeopleCreditSearch] = None)

object SearchOptions {
  val default = SearchOptions(
    rankingMode = SearchRankingMode.Popularity,
    thingTypeFilter = None
  )
}
