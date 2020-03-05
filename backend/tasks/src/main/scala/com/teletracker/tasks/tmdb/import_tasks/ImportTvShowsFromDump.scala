package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.model.tmdb.TvShow
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.tmdb.import_tasks.TvShowImportHandler.TvShowImportHandlerArgs
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}

class ImportTvShowsFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  genreCache: GenreCache,
  tvShowImportHandler: TvShowImportHandler
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[TvShow](
      s3,
      sourceRetriever,
      genreCache
    ) {

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: TvShow
  ): Future[Unit] = {
    tvShowImportHandler.handleItem(TvShowImportHandlerArgs(args.dryRun), item)
  }
}
