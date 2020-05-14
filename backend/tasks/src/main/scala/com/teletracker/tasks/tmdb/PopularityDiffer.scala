package com.teletracker.tasks.tmdb

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.model.MovieDumpFileRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import scala.collection.mutable
import scala.math.BigDecimal.RoundingMode

class PopularityDiffer @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: IngestJobParser)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val left = args.value[URI]("left").get
    val right = args.value[URI]("right").get
    val threshold = args.valueOrDefault("threshold", 1.0)

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

    val changeMap = new mutable.HashMap[Double, Int]()

    val finalCount = rightPopularityById.count {
      case (id, popularity) =>
        leftPopularityById.get(id) match {
          case None =>
            false
          case Some(popularityLeft) if popularity != popularityLeft =>
            val diff = {
              val abs = Math.abs(popularity - popularityLeft)
              BigDecimal(abs).setScale(3, RoundingMode.HALF_UP).doubleValue()
            }

            if (changeMap.isDefinedAt(diff)) {
              changeMap.update(diff, changeMap(diff) + 1)
            } else {
              changeMap += diff -> 1
            }

            diff > threshold

          case _ =>
            false
        }
    }

    logger.info(s"Total of ${finalCount} that changed")
//    logger.info(s"${changeMap}")
  }
}
