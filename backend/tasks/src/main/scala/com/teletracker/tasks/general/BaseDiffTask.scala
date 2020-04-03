package com.teletracker.tasks.general

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import io.circe.Decoder
import java.net.URI

abstract class BaseDiffTask[LeftType: Decoder, RightType: Decoder, Data](
  sourceRetriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {

  override protected def runInternal(args: Args): Unit = {
    val leftUri = args.valueOrThrow[URI]("left")
    val rightUri = args.valueOrThrow[URI]("right")
    val outputToFile = args.valueOrDefault[Boolean]("outputToFile", false)
    val fileName =
      if (outputToFile) Some(args.valueOrThrow[URI]("outputFileLocation"))
      else None

    val ingestJobParser = new IngestJobParser
    val rotater = FileRotator.everyNBytes(
      "diff",
      StorageUnit.fromMegabytes(10),
      fileName.map(_.getPath)
    )

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
                if (outputToFile) {
                  rotater.writeLine(dataToString(d))
                }
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

  protected def dataToString(data: Data): String = data.toString
}