package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.model.tmdb.Movie
import com.teletracker.common.process.tmdb.MovieImportHandler
import com.teletracker.common.util.Futures._
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.teletracker.common.util.Lists._
import com.twitter.util.StorageUnit
import io.circe.generic.JsonCodec
import io.circe.{Codec, Json}
import io.circe.syntax._
import com.teletracker.common.util.Functions._
import javax.inject.Inject
import java.net.URI
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

abstract class UpdateAllChangedItems[T: Codec](
  implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  @Inject private[this] var sourceRetriever: SourceRetriever = _
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  protected var isDryRun = false

  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val outputPath = rawArgs.valueOrThrow[String]("outputPath")
    val append = rawArgs.valueOrDefault("append", false)
    val perFileLimit = rawArgs.valueOrDefault("perFileLimit", -1)
    val limit = rawArgs.valueOrDefault("limit", -1)
    val offset = rawArgs.valueOrDefault("offset", 0)
    val parallelism = rawArgs.valueOrDefault("parallelism", 4)
    val perBatchSleepMs = rawArgs.value[Int]("perBatchSleepMs")
    val initialFileOffset = rawArgs.value[Int]("initialFileOffset")

    isDryRun = rawArgs.valueOrDefault("dryRun", false)

    val fileRotator = FileRotator.everyNBytes(
      "updates",
      StorageUnit.fromMegabytes(100),
      Some(outputPath),
      append = append
    )

    var isFirst = true

    sourceRetriever
      .getSourceStream(input)
      .drop(offset)
      .safeTake(limit)
      .foreach(source => {
        try {
          new IngestJobParser()
            .asyncStream[T](source.getLines())
            .applyIf(isFirst && initialFileOffset.isDefined)(stream => {
              isFirst = false
              stream.drop(initialFileOffset.get)
            })
            .safeTake(perFileLimit)
            .flatMapOption {
              case Left(value) =>
                logger.warn(s"Could not parse line: ${value}")
                None

              case Right(value) =>
                Some(value)
            }
            .grouped(parallelism)
            .delayedMapF(
              perBatchSleepMs.map(_ millis).getOrElse(0 millis),
              scheduler
            )(batch => {
              Future
                .sequence(batch.map(handleItem))
                .map(_.flatten)
                .map(_.map(_.noSpaces))
                .map(fileRotator.writeLines)
            })
            .force
            .await()
        } finally {
          source.close()
        }
      })

    fileRotator.finish()
  }

  protected def handleItem(item: T): Future[Option[Json]]
}

class UpdateAllChangedMovies @Inject()(
  movieImportHandler: MovieImportHandler
)(implicit executionContext: ExecutionContext)
    extends UpdateAllChangedItems[Movie] {
  override protected def handleItem(item: Movie): Future[Option[Json]] = {
    movieImportHandler
      .insertOrUpdate(
        MovieImportHandler.MovieImportHandlerArgs(
          forceDenorm = false,
          dryRun = isDryRun,
          verbose = false
        ),
        item
      )
      .map(result => {
        if (result.itemChanged) {
          if (result.itemIsNew) {
            logger.info(s"Found new item with tmdb id = ${item.id}")
          } else {
            logger.info(s"Found update item with tmdb id = ${item.id}")
          }

          result.jsonResult
            .flatMap(io.circe.parser.parse(_).toOption)
            .map(blob => {
              BackfillUpdateChange(
                isUpdate = !result.itemIsNew,
                needsDenorm = result.itemChanged,
                blob = blob
              ).asJson
            })
        } else {
          logger.info(s"Item tmdbid = ${item.id} did not change, skipping")
          None
        }
      })
  }
}

@JsonCodec
case class BackfillUpdateChange(
  isUpdate: Boolean,
  needsDenorm: Boolean,
  blob: Json)
