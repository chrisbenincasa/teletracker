package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.process.tmdb.PersonImportHandler.PersonImportHandlerArgs
import com.teletracker.common.process.tmdb.{PersonImportHandler, TmdbItemLookup}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificPerson @Inject()(
  itemExpander: TmdbItemLookup,
  personImportHandler: PersonImportHandler
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val id = args.valueOrThrow[Int]("id")
    val dryRun = args.valueOrDefault[Boolean]("dryRun", true)
    val async = args.valueOrDefault[Boolean]("async", false)

    itemExpander
      .expandPerson(id)
      .flatMap(movie => {
        personImportHandler.handleItem(
          PersonImportHandlerArgs(
            dryRun = dryRun,
            async = async
          ),
          movie
        )
      })
      .await()
  }
}
