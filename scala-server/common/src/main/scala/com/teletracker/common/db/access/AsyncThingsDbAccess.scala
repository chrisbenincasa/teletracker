package com.teletracker.common.db.access

import com.teletracker.common.db.DbMonitoring
import com.teletracker.common.db.model._
import com.teletracker.common.inject.{AsyncDbProvider, DbImplicits}
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AsyncThingsDbAccess @Inject()(
  override val provider: AsyncDbProvider,
  override val things: Things,
  override val tvShowSeasons: TvShowSeasons,
  override val thingNetworks: ThingNetworks,
  override val networks: Networks,
  override val networkReferences: NetworkReferences,
  override val tvShowEpisodes: TvShowEpisodes,
  override val availability: Availabilities,
  override val externalIds: ExternalIds,
  override val genres: Genres,
  override val genreReferences: GenreReferences,
  override val thingGenres: ThingGenres,
  override val availabilities: Availabilities,
  override val personThings: PersonThings,
  override val trackedListThings: TrackedListThings,
  override val trackedLists: TrackedLists,
  override val userThingTags: UserThingTags,
  override val collections: Collections,
  override val people: People,
  dbImplicits: DbImplicits,
  dbMonitoring: DbMonitoring
)(implicit executionContext: ExecutionContext)
    extends ThingsDbAccess(
      provider,
      things,
      tvShowSeasons,
      thingNetworks,
      networks,
      networkReferences,
      tvShowEpisodes,
      availability,
      externalIds,
      genres,
      genreReferences,
      thingGenres,
      availabilities,
      personThings,
      trackedListThings,
      trackedLists,
      userThingTags,
      collections,
      people,
      dbImplicits,
      dbMonitoring
    )
