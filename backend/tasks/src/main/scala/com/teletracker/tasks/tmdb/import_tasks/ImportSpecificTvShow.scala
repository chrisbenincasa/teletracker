package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.process.tmdb.{TmdbItemLookup, TvShowImportHandler}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificTvShow @Inject()(
  tvShowImportHandler: TvShowImportHandler,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val id = rawArgs.valueOrThrow[Int]("id")
    val dryRun = rawArgs.valueOrDefault[Boolean]("dryRun", true)
    val async = rawArgs.valueOrDefault("async", false)

    itemExpander
      .expandTvShow(id)
      .flatMap(tvShow => {
        tvShowImportHandler.handleItem(
          TvShowImportHandler.TvShowImportHandlerArgs(dryRun, async = async),
          tvShow
        )
      })
      .await()
  }
}
