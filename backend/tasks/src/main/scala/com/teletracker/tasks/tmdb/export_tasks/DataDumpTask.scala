package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.TeletrackerTaskApp
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Encoder}
import javax.inject.Inject
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Success
import scala.util.control.NonFatal

trait DataDumpTaskApp[T <: DataDumpTask[_, _]] extends TeletrackerTaskApp[T] {
  val file = flag[URI]("input", "The input dump file")
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The offset to start at")
  val flushEvery = flag[Int]("flushEvery", 1000, "The offset to start at")
  val rotateEvery = flag[Int]("rotateEvery", 10000, "The offset to start at")
}

@JsonCodec
@GenArgParser
case class DataDumpTaskArgs(
  input: URI,
  offset: Int = 0,
  limit: Int = -1,
  sleepMs: Int = 250,
  flushEvery: Int = 100,
  rotateEvery: Int = 1000,
  baseOutputPath: Option[String] = None,
  uploadToS3: Boolean = true)

object DataDumpTaskArgs

abstract class DataDumpTask[T: Decoder, Id](
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[DataDumpTaskArgs] {
  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _

  @Inject
  private[this] var sourceRetriever: SourceRetriever = _

  @Inject
  private[this] var s3: S3Client = _

  @Inject
  @SingleThreaded
  private[this] var scheduledExecutorService: ScheduledExecutorService = _

  private val dumpTime = Instant.now().toString

  override def runInternal(): Unit = {
    logger.info(
      s"Preparing to dump data to: s3://${teletrackerConfig.data.s3_bucket}/$fullPath/"
    )

    val rotater = FileRotator.everyNLines(
      baseFileName,
      args.rotateEvery,
      args.baseOutputPath
    )

    val processed = new AtomicLong(0)

    val parser = new IngestJobParser

    sourceRetriever
      .getSourceStream(args.input)
      .foreach(source => {
        try {
          parser
            .asyncStream[T](source.getLines())
            .flatMapOption {
              case Left(ex) =>
                logger.error(s"Error decoding", ex)
                None
              case Right(value) => Some(value)
            }
            .drop(args.offset)
            .safeTake(args.limit)
            .delayedForeachF(args.sleepMs millis, scheduledExecutorService)(
              thing => {
                getRawJson(getCurrentId(thing))
                  .map(
                    json => rotater.writeLine(json)
                  )
                  .recover {
                    case NonFatal(e) => {
                      logger.info(
                        s"Error retrieving ID: ${getCurrentId(thing)}\n${e.getMessage}"
                      )
                    }
                  }
                  .andThen {
                    case Success(_) =>
                      val total = processed.incrementAndGet()

                      if (total % args.flushEvery == 0) {
                        logger.info(s"Processed ${total} items so far.")
                      }
                  }
              }
            )
            .await()
        } finally {
          source.close()
        }
      })

    logger.info("Finished processing")

    rotater.finish()

    if (args.uploadToS3) {
      logger.info(s"Uploading results to: $s3Uri")
      sourceRetriever
        .getUriStream(rotater.baseUri)
        .filter(uri => uri.toString.contains(baseFileName))
        .map(new File(_))
        .foreach(uploadToS3)
    }
  }

  protected def getRawJson(currentId: Id): Future[String]

  protected def getCurrentId(item: T): Id

  protected def baseFileName: String

  protected def fullPath: String = s"data-dump/$baseFileName/$dumpTime"

  protected def s3Uri =
    new URI(s"s3://${teletrackerConfig.data.s3_bucket}/$fullPath")

  private def uploadToS3(file: File) = {
    // TODO: Gzip these
    logger.info(s"Uploading ${file.getAbsolutePath} to s3.")
    s3.putObject(
      PutObjectRequest
        .builder()
        .bucket(teletrackerConfig.data.s3_bucket)
        .key(s"$fullPath/${file.getName}")
        .contentType("text/plain")
//        .contentEncoding("gzip")
        .build(),
      RequestBody.fromFile(file)
    )
  }
}

trait TmdbDumpFileRow {
  def id: Int
  def popularity: Double
}

case class ResultWrapperType[T <: TmdbDumpFileRow](results: List[T])
