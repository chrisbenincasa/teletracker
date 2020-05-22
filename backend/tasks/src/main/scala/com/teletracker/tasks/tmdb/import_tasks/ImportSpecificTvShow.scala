package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.process.tmdb.{TmdbItemLookup, TvShowImportHandler}
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ImportSpecificTvShow @Inject()(
  tvShowImportHandler: TvShowImportHandler,
  itemExpander: TmdbItemLookup
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val id = args.valueOrThrow[Int]("id")
    val dryRun = args.valueOrDefault[Boolean]("dryRun", true)
    val async = args.valueOrDefault("async", false)

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
