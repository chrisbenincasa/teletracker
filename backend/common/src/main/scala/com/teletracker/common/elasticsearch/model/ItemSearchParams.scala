package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.dynamo.model.{
  StoredGenre,
  StoredNetwork,
  StoredUserList
}
import com.teletracker.common.db.model.ItemType
import com.teletracker.common.db.{Bookmark, SortMode}
import com.teletracker.common.elasticsearch.{
  AvailabilitySearch,
  PeopleCreditSearch
}
import com.teletracker.common.util.{ClosedNumericRange, OpenDateRange}

case class ItemSearchParams(
  genres: Option[Set[StoredGenre]],
  networks: Option[Set[StoredNetwork]],
  allNetworks: Option[Boolean],
  itemTypes: Option[Set[ItemType]],
  releaseYear: Option[OpenDateRange],
  peopleCredits: Option[PeopleCreditSearch],
  imdbRating: Option[ClosedNumericRange[Double]],
  tagFilters: Option[List[TagFilter]] = None,
  titleSearch: Option[String],
  sortMode: SortMode,
  limit: Int,
  bookmark: Option[Bookmark],
  forList: Option[StoredUserList],
  availability: Option[AvailabilitySearch])

case class TagFilter(
  tag: String,
  mustHave: Boolean)
