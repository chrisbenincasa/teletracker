package com.teletracker.tasks.tmdb.import_tasks

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.model.tmdb.Person
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportPeopleFromDump @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](storage, thingsDbAccess)
