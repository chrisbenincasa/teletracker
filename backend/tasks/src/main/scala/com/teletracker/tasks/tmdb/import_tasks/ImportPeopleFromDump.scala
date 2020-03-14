package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.model.ToEsItem
import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.process.tmdb.PersonImportHandler
import com.teletracker.common.process.tmdb.PersonImportHandler.PersonImportHandlerArgs
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ImportPeopleFromDump @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  genreCache: GenreCache,
  personImportHandler: PersonImportHandler
)(implicit protected val executionContext: ExecutionContext)
    extends ImportTmdbDumpTask[Person](
      s3,
      sourceRetriever,
      genreCache
    ) {

  implicit def toEsItem: ToEsItem[Person] = ToEsItem.forTmdbPerson

  override protected def shouldHandleItem(item: Person): Boolean =
    item.name.exists(_.nonEmpty)

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    person: Person
  ): Future[Unit] = {
    personImportHandler
      .handleItem(
        PersonImportHandlerArgs(scheduleDenorm = false, dryRun = args.dryRun),
        person
      )
      .map(_ => {})
      .recover {
        case NonFatal(e) =>
          logger.warn("Error occurred while processing person", e)
      }
  }
}
