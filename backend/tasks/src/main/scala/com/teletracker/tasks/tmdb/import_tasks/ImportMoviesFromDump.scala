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

  override protected def shouldHandleItem(item: Movie): Boolean = {
    item.adult.isEmpty || !item.adult.get
  }

  override protected def handleItem(item: Movie): Future[Unit] = {
    movieImportHandler
      .insertOrUpdate(
        MovieImportHandlerArgs(
          forceDenorm = false,
          dryRun = args.dryRun,
          insertsOnly = args.insertsOnly,
          async = true
        ),
        item
      )
      .map(_ => {})
      .recover {
        case NonFatal(e) =>
          logger.warn(
            s"Error occurred while processing movie (id = ${item.id})",
            e
          )
      }
  }

  override protected def handleDeletedItem(id: Int): Future[Unit] = {
    movieImportHandler
      .delete(
        MovieImportHandlerArgs(forceDenorm = false, dryRun = args.dryRun),
        id
      )
      .recover {
        case NonFatal(e) =>
          logger.warn(s"Error occurred while deleting movie (id = $id)", e)
      }
  }
}
