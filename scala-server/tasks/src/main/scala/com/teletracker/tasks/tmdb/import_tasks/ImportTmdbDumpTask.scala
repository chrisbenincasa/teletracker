package com.teletracker.tasks.tmdb.import_tasks

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.{Blob, BlobId, Storage}
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{ExternalSource, ThingLike}
import com.teletracker.common.model.Thingable
import com.teletracker.common.model.tmdb.HasTmdbId
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.TeletrackerTask
import io.circe.Decoder
import io.circe.parser._
import org.slf4j.LoggerFactory
import java.net.URI
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success}

abstract class ImportTmdbDumpTask[T <: HasTmdbId: Decoder](
  storage: Storage,
  thingsDbAccess: ThingsDbAccess
)(implicit executionContext: ExecutionContext,
  thingLike: Thingable[T])
    extends TeletrackerTask {
  protected val logger = LoggerFactory.getLogger(getClass)

  override def run(args: Args): Unit = {
    val file = args.value[URI]("input").get
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val parallelism = args.valueOrDefault("parallelism", 8)
    val perFileLimit = args.valueOrDefault("perFileLimit", -1)

    def sink[X](
      x: List[Option[X]],
      y: List[Option[X]]
    ) = List.empty[Option[X]]

    getSourceStream(file)
      .drop(offset)
      .safeTake(limit)
      .foreach(source => {
        try {
          SequentialFutures
            .batchedIterator(
              source.getLines().safeTake(perFileLimit),
              parallelism
            )(batch => {
              val processedBatch = batch.flatMap(line => {
                sanitizeLine(line).map(sanitizedLine => {
                  parse(sanitizedLine)
                    .flatMap(_.as[T]) match {
                    case Left(failure) =>
                      logger.error("Unexpected parsing error", failure)
                      Future.successful(None)

                    case Right(value) =>
                      thingLike.toThing(value) match {
                        case Success(thing) =>
                          val fut = thingsDbAccess
                            .saveThing(
                              thing,
                              Some(
                                ExternalSource.TheMovieDb -> value.id.toString
                              )
                            )

                          fut
                            .flatMap(
                              thing => extraWork(thing, value).map(_ => thing)
                            )
                            .map(Some(_))

                        case Failure(exception) =>
                          logger.error(
                            "Encountered unexpected exception",
                            exception
                          )
                          Future.successful(None)
                      }
                  }
                })
              })

              Future.sequence(processedBatch).map(_ => {})
            })
            .await()
        } finally {
          source.close()
        }
      })
  }

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