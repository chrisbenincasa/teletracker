package com.teletracker.tasks.tmdb.export_tasks

import com.teletracker.common.tasks.TeletrackerTask
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.TeletrackerTaskApp
import io.circe.{Decoder, Encoder}
import io.circe.parser._
import io.circe.generic.semiauto.deriveEncoder
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

trait DataDumpTaskApp[T <: DataDumpTask[_]] extends TeletrackerTaskApp[T] {
  val file = flag[URI]("input", "The input dump file")
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The offset to start at")
  val flushEvery = flag[Int]("flushEvery", 1000, "The offset to start at")
  val rotateEvery = flag[Int]("rotateEvery", 10000, "The offset to start at")
}

case class DataDumpTaskArgs(
  input: URI,
  offset: Int = 0,
  limit: Int = -1,
  sleepMs: Int = 250,
  flushEvery: Int = 100,
  rotateEvery: Int = 1000)

abstract class DataDumpTask[T <: TmdbDumpFileRow](
  s3: S3Client
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _

  private val dumpTime = Instant.now().toString

  override type TypedArgs = DataDumpTaskArgs

  implicit override protected def typedArgsEncoder: Encoder[DataDumpTaskArgs] =
    deriveEncoder[DataDumpTaskArgs]

  override def preparseArgs(args: Args): DataDumpTaskArgs = {
    DataDumpTaskArgs(
      input = args.value[URI]("input").get,
      offset = args.valueOrDefault[Int]("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      sleepMs = args.valueOrDefault("sleepMs", 250),
      flushEvery = args.valueOrDefault("flushEvery", 100),
      rotateEvery = args.valueOrDefault("rotateEvery", 1000)
    )
  }

  implicit protected def tDecoder: Decoder[T]

  override def runInternal(args: Args): Unit = {
    val file = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val sleepMs = args.valueOrDefault("sleepMs", 250)
    val flushEvery = args.valueOrDefault("flushEvery", 100)
    val rotateEvery = args.valueOrDefault("rotateEvery", 1000)

    var output: File = null
    var os: PrintStream = null

    logger.info(
      s"Preparing to dump data to: s3://${teletrackerConfig.data.s3_bucket}/$fullPath/"
    )

    def rotateFile(batch: Long): Unit = {
      synchronized {
        if (os ne null) {
          os.flush()
          os.close()
        }

        if (output ne null) {
          uploadToS3(output)
        }

        Try(output.delete())

        output = new File(f"$baseFileName.$batch%03d.json")
        os = new PrintStream(
          new BufferedOutputStream(
            new GZIPOutputStream(new FileOutputStream(output))
          )
        )
      }
    }

    rotateFile(0)

    val source = new SourceRetriever(s3).getSource(file)

    val processed = new AtomicLong(0)

    def dec(
      s: String,
      idx: Int
    ): List[T] = {
      decode[T](s) match {
        case Left(ex) =>
          logger.error(s"Error decoding at line: $idx", ex)
          Nil
        case Right(v) => List(v)
      }
    }

    source
      .getLines()
      .zipWithIndex
      .flatMap(Function.tupled(dec))
      .drop(offset)
      .safeTake(limit)
      .foreach(thing => {
        getRawJson(thing.id)
          .map(
            json =>
              os.synchronized {
                os.println(json)
              }
          )
          .recover {
            case NonFatal(e) => {
              logger.info(s"Error retrieving ID: ${thing.id}\n${e.getMessage}")
            }
          }
          .await()

        val total = processed.incrementAndGet()

        if (total % flushEvery == 0) {
          logger.info(s"Processed ${total} items so far.")
          os.flush()
        }

        if (total % rotateEvery == 0) {
          val suffix = total / rotateEvery
          rotateFile(suffix)
        }

        Thread.sleep(sleepMs)
      })

    os.flush()
    os.close()

    uploadToS3(output)

    source.close()
  }

  protected def getRawJson(currentId: Int): Future[String]

  protected def baseFileName: String

  protected def fullPath: String = s"data-dump/$baseFileName/$dumpTime"

  protected def s3Uri =
    new URI(s"s3://${teletrackerConfig.data.s3_bucket}/$fullPath")

  private def uploadToS3(file: File) = {
    s3.putObject(
      PutObjectRequest
        .builder()
        .bucket(teletrackerConfig.data.s3_bucket)
        .key(s"$fullPath/${file.getName}")
        .contentType("text/plain")
        .contentEncoding("gzip")
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

@JsonCodec
case class MovieDumpFileRow(
  adult: Boolean,
  id: Int,
  original_title: String,
  popularity: Double,
  video: Boolean)
    extends TmdbDumpFileRow

@JsonCodec
case class TvShowDumpFileRow(
  id: Int,
  original_name: String,
  popularity: Double)
    extends TmdbDumpFileRow