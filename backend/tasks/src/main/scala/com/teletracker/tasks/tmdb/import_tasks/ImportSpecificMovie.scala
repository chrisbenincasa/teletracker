package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.process.tmdb.{MovieImportHandler, TmdbItemLookup}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificMovie @Inject()(
  movieImportHandler: MovieImportHandler,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val id = rawArgs.valueOrThrow[Int]("id")
    val forceDenorm = rawArgs.valueOrDefault("forceDenorm", false)
    val async = rawArgs.valueOrDefault("async", false)
    val dryRun = rawArgs.valueOrDefault[Boolean]("dryRun", true)

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
