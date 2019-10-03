package com.teletracker.tasks.tmdb

import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  TmdbDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import com.twitter.util.Stopwatch
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
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
    val groupSize = args.valueOrDefault("groupSize", 10)
    val dryRun = args.valueOrDefault("dryRun", true)
    val popularityFloor = args.valueOrDefault("popularityFloor", 25.0)
    val source = sourceRetriever.getSource(file)

    val counter = new AtomicInteger()

    ingestJobParser
      .stream[T](
        source.getLines().drop(offset).safeTake(limit)
      )
      .collect {
        case Right(row) => row
        case Left(e)    => throw e
      }
      .takeWhile(_.popularity > popularityFloor)
      .grouped(groupSize)
      .foreach(batch => {
        val updates = batch.toList.map(item => {
          (item.id.toString, thingType, item.popularity)
        })

        val count = counter.addAndGet(updates.length)
        if (count % 50 == 0) {
          logger.info(s"Potentially updated ${count} so far.")
        }

        if (!dryRun) {
          thingsDbAccess
            .updatePopularitiesInBatch(updates)
            .await()
        } else {
          updates.foreach(update => logger.info(s"Would've updated: $update"))
        }
      })

    logger.info(
      s"${counter.get()} total items of type ${thingType} potentially updated"
    )
  }

  private def oldWay(args: Args) = {
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

class PopularityDiffer @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends TeletrackerTaskWithDefaultArgs {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit protected val tDecoder: Decoder[MovieDumpFileRow] =
    deriveCodec

  override def runInternal(args: Args): Unit = {
    val left = args.value[URI]("left").get
    val right = args.value[URI]("right").get

    val leftSource = sourceRetriever.getSource(left)
    val leftPopularityById = ingestJobParser
      .stream[MovieDumpFileRow](
        leftSource.getLines()
      )
      .collect {
        case Right(row) => row
        case Left(e)    => throw e
      }
      .map(row => {
        row.id -> row.popularity
      })
      .toMap

    val rightSource = sourceRetriever.getSource(right)
    val rightPopularityById = ingestJobParser
      .stream[MovieDumpFileRow](
        rightSource.getLines()
      )
      .collect {
        case Right(row) => row
        case Left(e)    => throw e
      }
      .map(row => {
        row.id -> row.popularity
      })
      .toMap

    val finalCount = rightPopularityById.count {
      case (id, popularity) =>
        leftPopularityById.get(id) match {
          case None =>
//            logger.info(s"Id = $id existed only on right")
            true
          case Some(popularityLeft) if popularity != popularityLeft =>
            val bigEnough = Math.abs(popularity - popularityLeft) > 1

            if (bigEnough) {
//              logger.info(
//                s"Id = $id popularity mismatch: left = $popularityLeft, right: $popularity"
//              )
            }

            bigEnough

          case _ =>
            false
        }
    }

    logger.info(s"Total of ${finalCount} that changed")
  }
}
