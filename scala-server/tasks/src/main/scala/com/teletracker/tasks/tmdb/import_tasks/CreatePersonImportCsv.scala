package com.teletracker.tasks.tmdb.import_tasks

import cats.syntax.writer
import com.google.api.gax.paging.Page
import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.{Blob, BlobId, Storage}
import com.teletracker.common.db.access.{AsyncThingsDbAccess, ThingsDbAccess}
import com.teletracker.common.db.model.ThingFactory
import com.teletracker.common.model.Thingable
import com.teletracker.common.model.tmdb.Person
import com.teletracker.tasks.TeletrackerTask
import javax.inject.Inject
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.Slug
import io.circe.parser.parse
import org.slf4j.LoggerFactory
import java.io.{BufferedWriter, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.collection.JavaConverters._

class CreatePersonImportCsv @Inject()(
  thingsDbAccess: AsyncThingsDbAccess,
  storage: Storage
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTask {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(args: Args): Unit = {
    val file = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)

    val output = new File(f"dump.csv")
    val writer = new BufferedWriter(
      new PrintWriter(new FileOutputStream(output))
    )

    val thingLike = implicitly[Thingable[Person]]

    getSourceStream(file)
      .drop(offset)
      .safeTake(limit)
      .flatMap(source => {
        for {
          line <- source.getLines()
          sanitizedLine <- sanitizeLine(line)
          personTry = parse(sanitizedLine).flatMap(_.as[Person])
        } yield {
          personTry.toTry
            .flatMap(thingLike.toThing)
            .map(t => {
              List(
                t.id.toString,
                t.name,
                t.normalizedName.toString,
                t.createdAt.toString,
                t.lastUpdatedAt.toString,
                t.metadata
                  .map(
                    _.noSpaces
                      .replaceAllLiterally("\\\"", "'")
                      .replaceAllLiterally("\"", "\"\"")
//                      .replaceAllLiterally("|", "\"")
                  )
                  .getOrElse(""),
                t.popularity.map(_.toString).getOrElse(""),
                t.tmdbId.getOrElse("")
              ).map(s => "\"" + s + "\"").mkString(",")
            })
            .recover {
              case t =>
                logger.error("Problem", t)
                throw t
            }
            .toOption
        }
      })
      .flatten
      .foreach(line => {
        writer.write(line)
        writer.newLine()
      })

    writer.flush()
    writer.close()
  }

  private def sanitizeLine(line: String): List[String] = {
    if (line.contains("}{")) {
      val left :: right :: Nil = line.split("}\\{", 2).toList
      (left + "}") :: ("{" + right) :: Nil
    } else {
      List(line)
    }
  }

  private def getSourceStream(uri: URI) = {
    uri.getScheme match {
      case "gs" =>
        val blob = storage
          .get(BlobId.of(uri.getHost, uri.getPath.stripPrefix("/")))

        val blobStream = if (blob == null) {
          val bucket = uri.getHost
          val folder = uri.getPath
          getBlobStreamForGsFolder(bucket, folder)
        } else {
          Stream(blob)
        }

        blobStream.map(blob => {
          logger.info(s"Preparing to ingest ${blob.getName}")
          Source.fromBytes(
            blob.getContent()
          )
        })
      case "file" =>
        Stream(Source.fromFile(uri))
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }

  private def getBlobStreamForGsFolder(
    bucket: String,
    folder: String
  ) = {
    var page: Page[Blob] = null
    var buf: Iterable[Blob] = Nil
    def getList: Option[Blob] = {
      if (buf.isEmpty) {
        if (page == null) {
          page = storage.list(
            bucket,
            BlobListOption.currentDirectory(),
            BlobListOption.prefix(folder.stripPrefix("/") + "/")
          )
        } else {
          page = page.getNextPage
        }

        val values = Option(page).map(_.getValues.asScala).getOrElse(Nil)
        if (values.isEmpty) {
          None
        } else {
          buf = values.tail
          Some(values.head)
        }
      } else {
        val value = buf.head
        buf = buf.tail
        Some(value)
      }
    }

    Stream
      .continually(getList)
      .takeWhile(_.isDefined)
      .map(_.get)
      .filterNot(_.isDirectory)
  }
}
