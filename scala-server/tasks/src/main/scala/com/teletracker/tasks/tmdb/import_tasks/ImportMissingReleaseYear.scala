package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.elasticsearch.{
  ItemLookup,
  ItemUpdater,
  PersonLookup
}
import com.teletracker.common.model.tmdb.{HasTmdbId, Movie, TvShow}
import com.teletracker.common.util.GenreCache
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class ImportMoviesMissingReleaseYear @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache,
  override protected val itemSearch: ItemLookup,
  override protected val itemUpdater: ItemUpdater,
  override protected val personLookup: PersonLookup
)(implicit override protected val executionContext: ExecutionContext)
    extends ImportMoviesFromDump(
      s3,
      sourceRetriever,
      thingsDbAccess,
      genreCache,
      itemSearch,
      itemUpdater,
      personLookup
    ) {

  private val counter = new AtomicInteger()
  private val resultCounter = new AtomicInteger()
  override protected def shouldHandleItem(item: Movie): Boolean = {
    val count = counter.incrementAndGet()
    if (count % 1000 == 0) logger.info(s"Checked ${count} items so far")
    val result = item.release_date.isEmpty || item.release_date.contains("")
    if (result) {
      val met = resultCounter.incrementAndGet()
      if (met % 1000 == 0) logger.info(s"Found ${count} items so far")
    }
    result
  }

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: Movie
  ): Future[Unit] = {
    if (item.release_date.exists(_.nonEmpty)) {
      Future.unit
    } else {
      super.handleItem(args, item)
    }
  }
}

class ImportTvMissingReleaseYear @Inject()(
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache,
  override protected val itemSearch: ItemLookup,
  override protected val itemUpdater: ItemUpdater,
  override protected val personLookup: PersonLookup
)(implicit override protected val executionContext: ExecutionContext)
    extends ImportTvShowsFromDump(
      s3,
      sourceRetriever,
      thingsDbAccess,
      genreCache,
      itemSearch,
      itemUpdater,
      personLookup
    ) {

  private val counter = new AtomicInteger()
  private val resultCounter = new AtomicInteger()

  override protected def shouldHandleItem(item: TvShow): Boolean = {
    val count = counter.incrementAndGet()
    if (count % 1000 == 0) logger.info(s"Checked ${count} items so far")
    val result = item.first_air_date.isEmpty || item.first_air_date.contains("")
    if (result) {
      val met = resultCounter.incrementAndGet()
      if (met % 1000 == 0) logger.info(s"Found ${count} items so far")
    }
    result
  }

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: TvShow
  ): Future[Unit] = {
    if (item.first_air_date.exists(_.nonEmpty)) {
      Future.unit
    } else {
      super.handleItem(args, item)
    }
  }
}
