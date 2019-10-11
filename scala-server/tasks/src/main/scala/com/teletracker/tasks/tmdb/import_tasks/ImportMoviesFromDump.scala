package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.util.GenreCache
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportMoviesFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Movie](storage, thingsDbAccess, genreCache)
