package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.process.tmdb.MovieImportHandler
import com.teletracker.common.process.tmdb.MovieImportHandler.MovieImportHandlerArgs
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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
    movieImportHandler
      .handleItem(
        MovieImportHandlerArgs(forceDenorm = false, dryRun = args.dryRun),
        item
      )
      .map(_ => {})
      .recover {
        case NonFatal(e) =>
          logger.warn("Error occurred while processing movie", e)
      }
  }
}
