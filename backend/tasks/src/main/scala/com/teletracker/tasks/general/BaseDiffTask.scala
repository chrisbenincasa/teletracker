package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Decoder
import java.net.URI

abstract class BaseDiffTask[LeftType: Decoder, RightType: Decoder, Data](
  sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {

  override protected def runInternal(args: Args): Unit = {
    val leftUri = args.valueOrThrow[URI]("left")
    val rightUri = args.valueOrThrow[URI]("right")

    val ingestJobParser = new IngestJobParser

    val leftData =
      sourceRetriever.getSourceStream(leftUri).foldLeft(Set.empty[Data]) {
        case (acc, source) =>
          try {
            acc ++ ingestJobParser
              .stream[LeftType](source.getLines())
              .flatMap {
                case Left(value) =>
                  logger.error("Could not parse line", value)
                  None
                case Right(value) =>
                  extractLeftData(value)
              }
              .toSet
          } finally {
            source.close()
          }
      }

    val total = sourceRetriever
      .getSourceStream(rightUri)
      .map(source => {
        try {
          ingestJobParser
            .stream[RightType](
              source.getLines()
            )
            .collect {
              case Right(row) => row
            }
            .flatMap(extractRightData)
            .flatMap(d => {
              if (!leftData(d)) {
                logger.info(
                  s"Missing data id = ${d}"
                )
                Some(d)
              } else {
                None
              }
            })
            .size
        } finally {
          source.close()
        }
      })
      .sum

    logger.info(s"Missing a total of ${total}.")
  }

  protected def extractLeftData(left: LeftType): Option[Data]

  protected def extractRightData(right: RightType): Option[Data]
}
