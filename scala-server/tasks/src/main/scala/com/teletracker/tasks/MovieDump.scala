package com.teletracker.tasks

import com.google.cloud.storage.{BlobId, BlobInfo, Storage}
import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser._
import io.circe.syntax._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.io.{BufferedWriter, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.control.NonFatal

object MovieDumpTool extends TeletrackerTaskApp[MovieDump] {
  val file = flag[URI]("input", "The input dump file")
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The offset to start at")
  val flushEvery = flag[Int]("flushEvery", 1000, "The offset to start at")
  val rotateEvery = flag[Int]("rotateEvery", 10000, "The offset to start at")
}

class MovieDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {

  private val logger = LoggerFactory.getLogger(getClass)
  private val dumpTime = Instant.now().toString

  override def run(args: Args): Unit = {
    implicit val thingyCodec = deriveCodec[Thingy]

    val file = args.value[URI]("input").get
    val offset = args.value[Int]("offset").get
    val limit = args.value[Int]("limit").get
    val flushEvery = args.valueOrDefault[Int]("flushEvery", 1000)
    val rotateEvery = args.valueOrDefault[Int]("rotateEvery", 10000)

    var output: File = null
    var writer: BufferedWriter = null

    def rotateFile(batch: Long): Unit = {
      synchronized {
        if (writer ne null) {
          writer.flush()
          writer.close()
        }

        if (output ne null) {
          uploadToGcp(output)
        }

        output = new File(s"movies.$batch.json")
        writer = new BufferedWriter(
          new PrintWriter(new FileOutputStream(output))
        )
      }
    }

    rotateFile(0)

    val source = getSource(file)

    val processed = new AtomicLong(0)

    source
      .getLines()
      .map(decode[Thingy](_).right.get)
      .drop(offset)
      .safeTake(limit)
      .foreach(thing => {
        itemExpander
          .expandMovie(thing.id, List("recommendations"))
          .map(_.asJson.noSpaces)
          .map(
            json =>
              writer.synchronized {
                writer.write(json)
                writer.newLine()
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
          writer.flush()
        }

        if (total % rotateEvery == 0) {
          val suffix = total / rotateEvery
          rotateFile(suffix)
        }

        Thread.sleep(250)
      })

    writer.flush()
    writer.close()

    source.close()
  }

  private def uploadToGcp(file: File) = {
    val writer = storage.writer(
      BlobInfo
        .newBuilder("teletracker", s"data-dump/$dumpTime/${file.getName}")
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
