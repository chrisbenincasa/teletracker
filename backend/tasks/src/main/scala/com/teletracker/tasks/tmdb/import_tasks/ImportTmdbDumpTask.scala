package com.teletracker.tasks.tmdb.import_tasks

import com.teletracker.common.model.tmdb.{ErrorResponse, HasTmdbId}
import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{AsyncStream, GenreCache}
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.util.SourceRetriever
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.OffsetDateTime
import java.util.concurrent.{ConcurrentLinkedQueue, Executors}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

object ImportTmdbDumpTaskArgs {
  def default(input: URI) = ImportTmdbDumpTaskArgs(
    input = input,
    dryRun = false,
    perBatchSleepMs = Some((1 second).toMillis.toInt)
  )
}

case class ImportTmdbDumpTaskArgs(
  input: URI,
  offset: Int = 0,
  limit: Int = -1,
  parallelism: Int = 4,
  perFileLimit: Int = -1,
  perBatchSleepMs: Option[Int] = None,
  dryRun: Boolean = true,
  insertsOnly: Boolean = false)

abstract class ImportTmdbDumpTask[T <: HasTmdbId](
  s3: S3Client,
  sourceRetriever: SourceRetriever,
  genreCache: GenreCache
)(implicit executionContext: ExecutionContext,
  decoder: Decoder[T])
    extends TeletrackerTask {

  import com.teletracker.common.util.json.circe._

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private val processedCounter = new AtomicInteger()
  private val failedCounter = new AtomicInteger()

  private val retryQueue = new ConcurrentLinkedQueue[T]()

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
      dryRun = args.valueOrDefault("dryRun", true),
      insertsOnly = args.valueOrDefault("insertsOnly", false)
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
      _,
      _
    ) = preparseArgs(args)

    sourceRetriever
      .getUriStream(file)
      .drop(offset)
      .safeTake(limit)
      .foreach(uri => {
        val source = sourceRetriever.getSource(uri, consultCache = true)
        try {
          AsyncStream
            .fromStream(source.getLines().toStream.zipWithIndex)
            .flatMapSeq {
              case (line, idx) => extractLine(line, idx, uri)
            }
            .filter {
              case Left(_)      => true
              case Right(value) => shouldHandleItem(value)
            }
            .safeTake(perFileLimit)
            .grouped(parallelism)
            .delayedMapF(
              perBatchSleepMs.map(_ millis).getOrElse(0 millis),
              scheduler
            )(handleBatch(typedArgs, _))
            .force
            .await()
        } finally {
          source.close()
        }
      })

    if (!retryQueue.isEmpty) {
      logger.info(s"Retrying ${retryQueue.size()} items.")
      AsyncStream
        .fromSeq(retryQueue.asScala.toSeq)
        .mapF(handleItem(typedArgs, _))
        .force
        .await()
    }

    logger.info(s"Successfully processed: ${processedCounter.get()} items.")
  }

  private def handleBatch(
    typedArgs: TypedArgs,
    batch: Seq[Either[Int, T]]
  ) = {
    Future
      .sequence(
        batch.map {
          case Left(id) =>
            handleDeletedItem(typedArgs, id)
              .map(_ => HandleDeleteResult(successful = true, id))
              .recover {
                case NonFatal(_) => HandleDeleteResult(successful = false, id)
              }
          case Right(item) =>
            handleItem(typedArgs, item)
              .map(_ => HandleItemResult(successful = true, item))
              .recover {
                case NonFatal(_) => HandleItemResult(successful = false, item)
              }
        }
      )
      .map(processes => {
        val numProcessed = processes.count(_.successful)
        val failed = processes.count(!_.successful)
        val totalProcessed = processedCounter.addAndGet(numProcessed)
        if (totalProcessed > 0 && totalProcessed % 500 == 0) {
          logger.info(s"Processed $totalProcessed items so far.")
        }
        val totalFailed = failedCounter.addAndGet(failed)
        if (totalFailed > 0 && totalFailed % 50 == 0) {
          logger.info(s"Failed ${totalFailed} items so far.")
        }

        retryQueue.addAll(processes.collect {
          case HandleItemResult(false, item) => item
        }.asJava)
      })
  }

  sealed private trait HandleResult {
    def successful: Boolean
  }
  private case class HandleItemResult(
    successful: Boolean,
    item: T)
      extends HandleResult
  private case class HandleDeleteResult(
    successful: Boolean,
    id: Int)
      extends HandleResult

  private def extractLine(
    line: String,
    index: Int,
    sourceUri: URI
  ) = {
    sanitizeLine(line).flatMap(sanitizedLine => {
      val parsed = parse(sanitizedLine)
      parsed
        .flatMap(_.as[T]) match {
        case Left(failure) =>
          parsed.flatMap(_.as[ErrorResponse]) match {
            case Left(_) =>
              logger.error(
                s"Unexpected parsing error on line ${index}\nJSON:${sanitizedLine} (source: ${sourceUri})",
                failure
              )
              None

            case Right(value)
                if value.status_code
                  .contains(34) && value.status_message
                  .exists(_.contains("not be found")) =>
              value.requested_item_id.map(id => Left(id))

            case Right(value) =>
              logger.error(
                s"Got unexpected error response from TMDb: ${value}"
              )
              None
          }

        case Right(value) =>
          Some(Right(value))
      }
    })
  }

  protected def handleItem(
    args: ImportTmdbDumpTaskArgs,
    item: T
  ): Future[Unit]

  protected def handleDeletedItem(
    args: ImportTmdbDumpTaskArgs,
    id: Int
  ): Future[Unit] = Future.unit

  protected def handleItemDeletion(
    args: ImportTmdbDumpTaskArgs,
    id: Int
  ): Future[Unit] = Future.unit

  protected def shouldHandleItem(item: T): Boolean = true

  private def sanitizeLine(line: String): List[String] = {
    if (line.contains("}{")) {
      val left :: right :: Nil = line.split("}\\{", 2).toList
      (left + "}") :: ("{" + right) :: Nil
    } else {
      List(line)
    }
  }
}
