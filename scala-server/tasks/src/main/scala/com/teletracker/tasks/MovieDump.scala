package com.teletracker.tasks

import com.google.cloud.storage.{BlobId, Storage}
import com.teletracker.common.process.tmdb.ItemExpander
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser._
import io.circe.syntax._
import javax.inject.Inject
import java.io.{BufferedWriter, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.control.NonFatal

object MovieDumpTool extends TeletrackerTaskApp[MovieDump] {
  val file = flag[URI]("input", "The input dump file")
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The offset to start at")
}

class MovieDump @Inject()(
  storage: Storage,
  itemExpander: ItemExpander
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {
  override def run(args: Args): Unit = {
    implicit val thingyCodec = deriveCodec[Thingy]

    val file = args.value[URI]("input").get
    val offset = args.value[Int]("offset").get
    val limit = args.value[Int]("limit").get

    val output = new File("output.json")
    val writer = new BufferedWriter(
      new PrintWriter(new FileOutputStream(output))
    )

    val source = Source
      .fromFile(file)

    val processed = new AtomicLong(0)

    getSource(file)
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
              println(s"Error retrieving ID: ${thing.id}\n${e.getMessage}")
            }
          }
          .await()

        val total = processed.incrementAndGet()
        if (total % 1000 == 0) {
          println(s"Processed ${total} items so far.")
          writer.flush()
        }

        Thread.sleep(250)
      })

    writer.flush()
    writer.close()

    source.close()
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
          s"Unsupposed file scheme: ${uri.getScheme}"
        )
    }
  }
}
