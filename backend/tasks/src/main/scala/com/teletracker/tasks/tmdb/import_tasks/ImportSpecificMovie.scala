package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.process.tmdb.{MovieImportHandler, TmdbItemLookup}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificMovie @Inject()(
  movieImportHandler: MovieImportHandler,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val id = args.valueOrThrow[Int]("id")
    val forceDenorm = args.valueOrDefault("forceDenorm", false)
    val async = args.valueOrDefault("async", false)
    val dryRun = args.valueOrDefault[Boolean]("dryRun", true)

    itemExpander
      .expandMovie(id)
      .flatMap(movie => {
        movieImportHandler.insertOrUpdate(
          MovieImportHandler
            .MovieImportHandlerArgs(
              forceDenorm = forceDenorm,
              dryRun = dryRun,
              async = async
            ),
          movie
        )
      })
      .await()
  }
}
