package com.teletracker.tasks.tmdb.export_tasks

import cats.syntax
import com.google.cloud.storage.{BlobId, BlobInfo, Storage}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import io.circe.Decoder
import io.circe.parser._
import io.circe.generic.semiauto.deriveCodec
import org.slf4j.LoggerFactory
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Try
import scala.util.control.NonFatal

trait DataDumpTaskApp[T <: DataDumpTask[_]] extends TeletrackerTaskApp[T] {
  val file = flag[URI]("input", "The input dump file")
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The offset to start at")
  val flushEvery = flag[Int]("flushEvery", 1000, "The offset to start at")
  val rotateEvery = flag[Int]("rotateEvery", 10000, "The offset to start at")
}

abstract class DataDumpTask[T <: TmdbDumpFileRow](
  storage: Storage
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  private val logger = LoggerFactory.getLogger(getClass)
  private val dumpTime = Instant.now().toString

  implicit protected def tDecoder: Decoder[T]

  override def run(args: Args): Unit = {
    val file = args.value[URI]("input").get
    val offset = args.value[Int]("offset").get
    val limit = args.valueOrDefault("limit", -1)
    val sleepMs = args.valueOrDefault("sleepMs", 250)
    val flushEvery = args.valueOrDefault("flushEvery", 100)
    val rotateEvery = args.valueOrDefault("rotateEvery", 1000)

    var output: File = null
    var os: PrintStream = null

    def rotateFile(batch: Long): Unit = {
      synchronized {
        if (os ne null) {
          os.flush()
          os.close()
        }

        if (output ne null) {
          uploadToGcp(output)
        }

        Try(output.delete())

        output = new File(f"$baseFileName.$batch%03d.json")
        os = new PrintStream(
          new BufferedOutputStream(new FileOutputStream(output))
        )
      }
    }

    rotateFile(0)

    val source = getSource(file)

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

    source.close()

    sys.exit(0)
  }

  protected def getRawJson(currentId: Int): Future[String]

  protected def baseFileName: String

  private def uploadToGcp(file: File) = {
    val writer = storage.writer(
      BlobInfo
        .newBuilder(
          "teletracker",
          s"data-dump/$baseFileName/$dumpTime/${file.getName}"
        )
        .setContentType("text/plain")
        .build()
    )

    val source = Source.fromFile(file).getLines()

    source
      .grouped(5)
      .foreach(lines => {
        val finalString = lines.mkString("\n")
        val bb = ByteBuffer.wrap(finalString.getBytes)
        writer.write(bb)
      })

    writer.close()
  }

  private def getSource(uri: URI): Source = {
    uri.getScheme match {
      case "gs" =>
        Source.fromBytes(
          storage
            .get(BlobId.of(uri.getHost, uri.getPath.stripPrefix("/")))
            .getContent()
        )
      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }
}

trait TmdbDumpFileRow {
  def id: Int
}

case class ResultWrapperType[T <: TmdbDumpFileRow](results: List[T])

case class MovieDumpFileRow(
  adult: Boolean,
  id: Int,
  original_title: String,
  popularity: Double,
  video: Boolean)
    extends TmdbDumpFileRow

case class TvShowDumpFileRow(
  id: Int,
  original_name: String,
  popularity: Double)
    extends TmdbDumpFileRow
