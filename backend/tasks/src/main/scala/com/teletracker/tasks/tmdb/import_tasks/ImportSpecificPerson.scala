package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.process.tmdb.PersonImportHandler.PersonImportHandlerArgs
import com.teletracker.common.process.tmdb.{ItemExpander, PersonImportHandler}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificPerson @Inject()(
  itemExpander: ItemExpander,
  personImportHandler: PersonImportHandler
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val id = args.valueOrThrow[Int]("id")
    val dryRun = args.valueOrDefault[Boolean]("dryRun", true)

    itemExpander
      .expandPerson(id)
      .flatMap(movie => {
        personImportHandler.handleItem(
          PersonImportHandlerArgs(
            scheduleDenorm = options.scheduleFollowupTasks,
            dryRun = dryRun
          ),
          movie
        )
      })
      .await()
  }
}