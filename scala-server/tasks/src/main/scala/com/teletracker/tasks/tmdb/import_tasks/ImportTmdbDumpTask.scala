package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ExternalSource, ThingLike}
import com.teletracker.common.model.tmdb.{ErrorResponse, HasTmdbId}
import com.teletracker.common.model.{Thingable, ToEsItem}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.GenreCache
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.TeletrackerTask
import com.teletracker.tasks.util.SourceRetriever
import diffson.lcs.Patience
import io.circe.parser._
import io.circe.{Decoder, Encoder, Json}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class ImportTmdbDumpTaskArgs(
  input: URI,
  offset: Int = 0,
  limit: Int = -1,
  parallelism: Int = 4,
  perFileLimit: Int = -1,
  perBatchSleepMs: Option[Int] = None,
  dryRun: Boolean = true)

abstract class ImportTmdbDumpTask[T <: HasTmdbId](
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  thingsDbAccess: ThingsDbAccess,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext,
  thingLike: Thingable[T],
  decoder: Decoder[T])
    extends TeletrackerTask {

  import com.teletracker.common.util.json.circe._

  protected val logger = LoggerFactory.getLogger(getClass)
  private val processedCounter = new AtomicInteger()

  override type TypedArgs = ImportTmdbDumpTaskArgs

  implicit override protected def typedArgsEncoder
    : Encoder[ImportTmdbDumpTaskArgs] =
    io.circe.generic.semiauto.deriveEncoder[ImportTmdbDumpTaskArgs]

  override def preparseArgs(args: Args): ImportTmdbDumpTaskArgs = {
    ImportTmdbDumpTaskArgs(
      input = args.value[URI]("input").get,
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      parallelism = args.valueOrDefault("parallelism", 4),
      perFileLimit = args.valueOrDefault("perFileLimit", -1),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs"),
      dryRun = args.valueOrDefault("dryRun", true)
    )
  }

  override def runInternal(args: Args): Unit = {
    val typedArgs @ ImportTmdbDumpTaskArgs(
      file,
      offset,
      limit,
      parallelism,
      perFileLimit,
      perBatchSleepMs,
      _
    ) = preparseArgs(args)

    sourceRetriever
      .getSourceStream(file)
      .drop(offset)
      .safeTake(limit)
      .foreach(source => {
        try {
          SequentialFutures
            .batchedIterator(
              source
                .getLines()
                .zipWithIndex
                .filter(_._1.nonEmpty)
                .flatMap(Function.tupled(extractLine))
                .filter(shouldHandleItem)
                .safeTake(perFileLimit),
              parallelism,
              perElementWait = perBatchSleepMs.map(_ millis)
            )(batch => {
              Future
                .sequence(
                  batch.map(handleItem(typedArgs, _))
                )
                .map(processes => {
                  val numProcessed = processes.size
                  val totalProcessed = processedCounter.addAndGet(numProcessed)
                  if (totalProcessed % 500 == 0) {
                    logger.info(s"Processed $totalProcessed items so far.")
                  }
                })
            })
            .await()
        } finally {
          source.close()
        }
      })

    logger.info(s"Successfully processed: ${processedCounter.get()} items.")
  }

  private def extractLine(
    line: String,
    index: Int
  ) = {
    sanitizeLine(line).flatMap(sanitizedLine => {
      val parsed = parse(sanitizedLine)
      parsed
        .flatMap(_.as[T]) match {
        case Left(failure) =>
          parsed.flatMap(_.as[ErrorResponse]) match {
            case Left(_) =>
              logger.error(
                s"Unexpected parsing error on line ${index}\nJSON:${sanitizedLine}",
                failure
              )
              None

            case Right(value)
                if value.status_code
                  .contains(34) && value.status_message
                  .exists(_.contains("not be found")) =>
              // TODO: Handle item deletion
              None

            case Right(value) =>
              logger.error(
                s"Got unexpected error response from TMDb: ${value}"
              )
              None
          }

        case Right(value) =>
          Some(value)
      }
    })
  }

  protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: T
  ): Future[Unit] = {
    genreCache
      .get()
      .flatMap(genres => {
        thingLike.toThing(item) match {
          case Success(thing) =>
            val genreIds = thing.metadata
              .flatMap(
                Paths.applyPaths(
                  _,
                  Paths.MovieGenres,
                  Paths.ShowGenres
                )
              )
              .map(_.map(_.id))
              .map(
                _.flatMap(
                  id =>
                    genres.get(
                      ExternalSource.TheMovieDb -> id.toString
                    )
                )
              )
              .map(_.map(_.id))
              .getOrElse(Nil)
              .flatten
              .toSet

            val fut = thingsDbAccess
              .saveThing(
                thing.withGenres(genreIds),
                Some(
                  ExternalSource.TheMovieDb -> item.id.toString
                )
              )

            fut
              .flatMap(
                thing => extraWork(thing, item).map(_ => thing)
              )
              .map(Some(_))

          case Failure(exception) =>
            logger.error(
              "Encountered unexpected exception",
              exception
            )
            Future.successful(None)
        }
      })
  }

  protected def handleItemDeletion(
    args: ImportTmdbDumpTaskArgs,
    id: Int
  ): Future[Unit] = Future.unit

  protected def shouldHandleItem(item: T): Boolean = true

  protected def extraWork(
    thingLike: ThingLike,
    entity: T
  ): Future[Unit] = Future.unit

  private def sanitizeLine(line: String): List[String] = {
    if (line.contains("}{")) {
      val left :: right :: Nil = line.split("}\\{", 2).toList
      (left + "}") :: ("{" + right) :: Nil
    } else {
      List(line)
    }
  }
}

trait ImportTmdbDumpTaskToElasticsearch[T <: HasTmdbId] {
  self: ImportTmdbDumpTask[T] =>

  implicit def toEsItem: ToEsItem[T]

  implicit lazy val lcs = new Patience[Json]
  implicit protected val executionContext: ExecutionContext

  override protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: T
  ): Future[Unit]

  protected def getNow(): OffsetDateTime = OffsetDateTime.now()
}
