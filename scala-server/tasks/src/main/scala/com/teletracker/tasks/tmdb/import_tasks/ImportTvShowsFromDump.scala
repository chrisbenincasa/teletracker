package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.{AsyncThingsDbAccess, ThingsDbAccess}
import com.teletracker.common.model.tmdb.TvShow
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportTvShowsFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: AsyncThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[TvShow](storage, thingsDbAccess)
