package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import io.circe.syntax._
import io.circe.{Codec, Decoder, Json}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}

abstract class CreateBackfillUpdateFile[T: Decoder](
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val regionString = args.valueOrDefault("region", "us-west-2")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault[Int]("limit", -1)
    val perFileLimit = args.valueOrDefault[Int]("perFileLimit", -1)
    val append = args.valueOrDefault[Boolean]("append", false)
    val region = Region.of(regionString)
    val gteFilter = args.value[String]("gteFilter")
    val ltFilter = args.value[String]("ltFilter")
    val outputPath = args.valueOrThrow[String]("outputPath")
    val parallelism = args.valueOrDefault("parallelism", 1)

    val s3 = S3Client.builder().region(region).build()

    val retriever = new SourceRetriever(s3)

    val fileRotator = FileRotator.everyNBytes(
      "updates",
      StorageUnit.fromMegabytes(100),
      Some(outputPath),
      append = append
    )

    def filter(uri: URI) = {
      lazy val sanitized = uri.getPath.stripPrefix("/")
      gteFilter.forall(f => sanitized >= f) &&
      ltFilter.forall(f => sanitized < f)
    }

    val seen = ConcurrentHashMap.newKeySet[String]()

    if (append) {
      prePopulateSeenSet(retriever, seen, outputPath)
    }

    val uris = retriever
      .getUriStream(input, filter = filter, offset = offset, limit = limit)
      .sorted
      .reverse
      .toList

    logger.info("Sup")

    AsyncStream
      .fromSeq(uris.toStream)
      .map(retriever.getSource(_))
      .foreachConcurrent(parallelism)(source => {
        Future {
          try {
            new IngestJobParser()
              .asyncStream[T](source.getLines())
              .collect {
                case Right(value)
                    if shouldKeepItem(value) && seen.add(uniqueId(value)) =>
                  value
              }
              .safeTake(perFileLimit)
              .foreachConcurrent(8)(value => {
                val row = makeBackfillRow(value)

                Future.successful {
                  fileRotator.writeLines(
                    Seq(row.asJson.noSpaces)
                  )
                }
              })
              .await()
          } finally {
            source.close()
          }
        }
      })
      .await()

    fileRotator.finish()
  }

  protected def uniqueId(item: T): String

  protected def shouldKeepItem(item: T): Boolean

  protected def makeBackfillRow(item: T): TmdbBackfillOutputRow

  private def prePopulateSeenSet(
    retriever: SourceRetriever,
    set: java.util.Set[String],
    path: String
  ): Unit = {
    val outputLoc = new File(path).toURI
    retriever
      .getSourceAsyncStream(outputLoc)
      .foreachConcurrent(8)(source => {
        Future {
          try {
            new IngestJobParser()
              .stream[TmdbBackfillOutputRow](source.getLines())
              .collect {
                case Right(value) => value.tmdbId
              }
              .map(_.toString)
              .foreach(set.add)
          } finally {
            source.close()
          }
        }
      })
      .await()
  }
}

object TmdbBackfillOutputRow {
  implicit val codec: Codec[TmdbBackfillOutputRow] =
    io.circe.generic.semiauto.deriveCodec
}
case class TmdbBackfillOutputRow(
  tmdbId: Int,
  partialJson: Json)
