package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.tmdb.TvShow
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportTvShowsFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[TvShow](storage, thingsDbAccess)
