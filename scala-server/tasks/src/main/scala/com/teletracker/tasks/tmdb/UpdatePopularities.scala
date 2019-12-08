package com.teletracker.tasks.tmdb

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  MovieDumpFileRow,
  TmdbDumpFileRow,
  TvShowDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Decoder
import io.circe.generic.semiauto.deriveCodec
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

class UpdateMoviePopularities @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends UpdatePopularities[MovieDumpFileRow](
      sourceRetriever,
      ingestJobParser
    ) {

  override protected val tDecoder: Decoder[MovieDumpFileRow] =
    deriveCodec

  override protected def thingType: ThingType = ThingType.Movie
}

class UpdateTvShowPopularities @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends UpdatePopularities[TvShowDumpFileRow](
      sourceRetriever,
      ingestJobParser
    ) {

  override protected val tDecoder: Decoder[TvShowDumpFileRow] =
    deriveCodec

  override protected def thingType: ThingType = ThingType.Show
}

abstract class UpdatePopularities[T <: TmdbDumpFileRow](
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends TeletrackerTaskWithDefaultArgs {
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
//          thingsDbAccess
//            .updatePopularitiesInBatch(updates)
//            .await()

          // TODO: Elasticseaerch
          Future.unit
        } else {
          updates.foreach(update => logger.info(s"Would've updated: $update"))
        }
      })

    logger.info(
      s"${counter.get()} total items of type ${thingType} potentially updated"
    )
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
