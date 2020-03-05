package com.teletracker.common.db

import com.teletracker.common.api.model.TrackedList
import com.teletracker.common.db.dynamo.model.{StoredGenre, StoredNetwork}
import com.teletracker.common.db.model.{PartialThing, ThingType, UserThingTag}
import com.teletracker.common.elasticsearch.PeopleCreditSearch
import com.teletracker.common.util.OpenDateRange

object UserThingDetails {
  def empty: UserThingDetails = UserThingDetails(Seq())
}

case class UserThingDetails(
  belongsToLists: Seq[TrackedList],
  tags: Seq[UserThingTag] = Seq.empty)

case class RecentAvailability(
  recentlyAdded: Seq[PartialThing],
  future: FutureAvailability)

case class FutureAvailability(
  upcoming: Seq[PartialThing],
  expiring: Seq[PartialThing])

case class SearchOptions(
  rankingMode: SearchRankingMode,
  thingTypeFilter: Option[Set[ThingType]],
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
