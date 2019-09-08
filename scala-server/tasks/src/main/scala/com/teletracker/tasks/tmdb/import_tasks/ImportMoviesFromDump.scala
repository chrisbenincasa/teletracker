package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.AsyncThingsDbAccess
import com.teletracker.common.model.tmdb.Movie
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportMoviesFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: AsyncThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Movie](storage, thingsDbAccess)
