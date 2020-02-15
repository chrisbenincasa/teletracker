package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.tmdb.import_tasks.MovieImportHandler.MovieImportHandlerArgs
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}

class ImportMoviesFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  genreCache: GenreCache,
  movieImportHandler: MovieImportHandler
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Movie](
      s3,
      sourceRetriever,
      genreCache
    ) {

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: Movie
  ): Future[Unit] = {
    movieImportHandler.handleItem(MovieImportHandlerArgs(args.dryRun), item)
  }
}
