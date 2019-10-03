package com.teletracker.tasks.tmdb

import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  TmdbDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import com.teletracker.common.util.Futures._
import com.twitter.util.Stopwatch
import com.teletracker.common.util.Lists._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import scala.util.control.NonFatal

class UpdateMoviePopularities @Inject()(
  dbProvider: BaseDbProvider,
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser,
  thingsDbAccess: ThingsDbAccess)
    extends UpdatePopularities[MovieDumpFileRow](
      dbProvider,
      sourceRetriever,
      ingestJobParser,
      thingsDbAccess
    ) {

  override protected val tDecoder: Decoder[MovieDumpFileRow] =
    deriveCodec

  override protected def thingType: ThingType = ThingType.Movie
}

class UpdateTvShowPopularities @Inject()(
  dbProvider: BaseDbProvider,
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser,
  thingsDbAccess: ThingsDbAccess)
    extends UpdatePopularities[TvShowDumpFileRow](
      dbProvider,
      sourceRetriever,
      ingestJobParser,
      thingsDbAccess
    ) {

  override protected val tDecoder: Decoder[TvShowDumpFileRow] =
    deriveCodec

  override protected def thingType: ThingType = ThingType.Show
}

abstract class UpdatePopularities[T <: TmdbDumpFileRow](
  dbProvider: BaseDbProvider,
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser,
  thingsDbAccess: ThingsDbAccess)
    extends TeletrackerTaskWithDefaultArgs {
  import dbProvider.driver.api._

  private val logger = LoggerFactory.getLogger(getClass)

  implicit protected val tDecoder: Decoder[T]

  protected def thingType: ThingType

  override def runInternal(args: Args): Unit = {
    // Input must be SORTED BY POPULARITY DESC
    val file = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val groupSize = args.valueOrDefault("groupSize", 1000)
    val source = sourceRetriever.getSource(file)

    dbProvider.getDB
      .run {
        sqlu"""
          CREATE TEMP TABLE update_popularities (
            tmdb_id VARCHAR PRIMARY KEY,
            popularity DOUBLE PRECISION
          );
    """
      }
      .await()

    dbProvider.getDB
      .run {
        sqlu"""
          CREATE INDEX "update_popularities_idx" ON "update_popularities" ("popularity" DESC NULLS LAST);
        """
      }
      .await()

    try {
      val popularities = ingestJobParser
        .stream[T](
          source.getLines().drop(offset).safeTake(limit)
        )
        .collect {
          case Right(row) => row
        }
        .grouped(groupSize)
        .map(batch => {
          val update = batch.map(row => {
            row.id.toString -> row.popularity
          })
          val values = update
            .map {
              case (tmdbId, popularity) => s"('$tmdbId', $popularity)"
            }
            .mkString(",")

          val elapsed = Stopwatch.start()

          dbProvider.getDB
            .run {
              sqlu"""
               INSERT INTO update_popularities VALUES #$values;
             """
            }
            .await()

          logger.info(s"It took ${elapsed().inMillis}ms to insert 1000 rows")

          batch.head.popularity
        })
        .toList

      popularities
        .groupBy(identity)
        .map {
          case (popularity, num) => popularity -> num.size
        }
        .toList
        .sortBy(-_._1)
        .foreach {
          case (popularity, limitMultiplier) =>
            val limit = groupSize * limitMultiplier
            logger.info(s"Updating popularities < $popularity")
            val elapsed = Stopwatch.start()
            val updated = dbProvider.getDB
              .run {
                sqlu"""
             UPDATE things SET popularity = x.popularity FROM 
             (SELECT tmdb_id, popularity FROM update_popularities WHERE popularity <= $popularity ORDER BY popularity DESC LIMIT #$limit) AS x 
             WHERE x.tmdb_id = things.tmdb_id AND things.type = ${thingType.toString};
           """
              }
              .await()

            logger.info(
              s"It took ${elapsed().inMillis}ms to update $updated rows"
            )
        }
    } catch {
      case NonFatal(e) =>
        logger.error("Unexpected error while running update job", e)
    }
  }
}
