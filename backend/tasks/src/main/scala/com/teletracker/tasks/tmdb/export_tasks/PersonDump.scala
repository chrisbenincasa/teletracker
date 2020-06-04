package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.process.tmdb.TmdbItemLookup
import com.teletracker.tasks.model.PersonDumpFileRow
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

object PersonDumpTool extends DataDumpTaskApp[PersonDump]

class PersonDump @Inject()(
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends DataDumpTask[PersonDumpFileRow, Int] {

  override protected val baseFileName = "people"

  override protected def getRawJson(currentId: Int): Future[String] = {
    itemExpander
      .expandPersonRaw(currentId)
      .map(_.noSpaces)
  }

  override protected def getCurrentId(item: PersonDumpFileRow): Int = item.id
}
