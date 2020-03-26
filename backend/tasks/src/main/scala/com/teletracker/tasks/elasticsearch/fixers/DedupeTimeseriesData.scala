package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.util.Futures._
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.elasticsearch.FileRotator
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import com.twitter.util.StorageUnit
import javax.inject.Inject
import java.net.URI
import java.nio.file.Paths
import io.circe.syntax._
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

class DedupeTimeseriesData @Inject()(
  sourceRetriever: SourceRetriever
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val prefix = args.value[String]("prefix")
    val suffix = args.value[String]("suffix")
    val indexPosition = args.valueOrThrow[Int]("indexPos")
    val splitChar = args.valueOrDefault[String]("splitChar", ".").head

    val deduped = new AtomicInteger()

    val fileRotator = FileRotator.everyNBytes(
      "alternative-title-updates-deduped",
      StorageUnit.fromMegabytes(100),
      Some("alternative-titles")
    )

    val seen = ConcurrentHashMap.newKeySet[Int]()

    sourceRetriever
      .getUriStream(
        input,
        uri => {
          val str = Paths.get(uri).getFileName.toString
          val prefixCheck = prefix.forall(str.startsWith)
          val suffixCheck = suffix.forall(str.endsWith)
          prefixCheck && suffixCheck
        }
      )
      .toList
      .sortBy(uri => {
        val fileName = Paths.get(uri).getFileName
        fileName.toString.split(splitChar).apply(indexPosition).toInt
      })(Ordering[Int].reverse)
      .toStream
      .map(sourceRetriever.getSource(_))
      .foreach(source => {
        try {
          new IngestJobParser()
            .asyncStream[TmdbBackfillOutputRow](source.getLines())
            .collect {
              case Right(value) => value
            }
            .foreachConcurrent(8)(row => {
              if (seen.add(row.tmdbId)) {
                Future.successful {
                  fileRotator.writeLines(
                    Seq(
                      row.asJson.noSpaces
                    )
                  )
                }
              } else {
                deduped.incrementAndGet()
                Future.successful {
                  logger.info(s"Deduped id ${row.tmdbId}")
                }
              }
            })
            .await()
        } finally {
          source.close()
        }
      })

    logger.info(s"Deduped ${deduped.get()} items.")
  }
}
