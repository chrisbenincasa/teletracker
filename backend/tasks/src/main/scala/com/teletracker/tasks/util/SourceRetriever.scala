package com.teletracker.tasks.util

import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Lists._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  ListObjectsV2Request,
  NoSuchKeyException,
  S3Object
}
import software.amazon.awssdk.utils.IoUtils
import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URI
import java.nio.file.{Files, Paths}
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.compat.java8.StreamConverters._
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.compat.java8.StreamConverters._

class SourceRetriever @Inject()(s3: S3Client) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val localFsCache = new ConcurrentHashMap[String, URI]()

  def getSource(
    uri: URI,
    consultCache: Boolean = false
  ): Source = {
    uri.getScheme match {
      case "s3" =>
        logger.info(s"Pulling s3://${uri.getHost}/${uri.getPath}")
        getS3Object(uri.getHost, uri.getPath, consultCache)

      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }

  def getS3Object(
    bucket: String,
    key: String,
    consultCache: Boolean = false
  ): Source = {
    def getS3ObjectInner() = {
      val tmpFile = File.createTempFile(s"${bucket}_${key}", ".tmp.txt")
      val stream = withRetries(5) {
        s3.getObject(
          GetObjectRequest
            .builder()
            .bucket(bucket)
            .key(key.stripPrefix("/"))
            .build()
        )
      } {
        case _: NoSuchKeyException =>
          false

        case e: SdkClientException if e.retryable() =>
          true

        case NonFatal(e) =>
          logger.error(
            s"Failed to get object: s3://${bucket}/${key.stripPrefix("/")}",
            e
          )

          true
      }

      val isGzip = stream.response().contentEncoding() == "gzip" ||
        stream.response().contentType() == "application/gzip"

      val finalStream = if (isGzip) {
        new GZIPInputStream(stream)
      } else {
        stream
      }

      val fos = new FileOutputStream(tmpFile)
      try {
        IoUtils.copy(finalStream, fos)
      } finally {
        fos.flush()
        fos.close()
      }

      localFsCache.put(s"${bucket}_${key}", tmpFile.toURI)

      Source.fromInputStream(new FileInputStream(tmpFile))
    }

    if (consultCache) {
      Option(localFsCache.get(s"${bucket}_${key}")) match {
        case Some(value) =>
          val f = new File(value)
          if (!f.exists()) {
            getS3ObjectInner()
          } else {
            Source.fromFile(f)
          }
        case None =>
          getS3ObjectInner()
      }
    } else {
      getS3ObjectInner()
    }
  }

  def getUriStream(
    baseUri: URI,
    filter: URI => Boolean = _ => true,
    offset: Int = 0,
    limit: Int = -1
  ): Stream[URI] = {
    baseUri.getScheme match {
      case "s3" =>
        getS3ObjectStream(baseUri.getHost, baseUri.getPath)
          .flatMap(
            obj =>
              Stream(URI.create(s"s3://${baseUri.getHost}/${obj.key}"))
                .filter(filter)
          )
          .drop(offset)
          .safeTake(limit)
      case "file" =>
        val file = new File(baseUri)
        if (file.isDirectory) {
          getFilePathStream(baseUri, 1, filter, offset, limit).map(_.toUri)
        } else if (filter(baseUri)) {
          Stream(baseUri)
        } else {
          Stream.empty
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupposed file scheme: ${baseUri.getScheme}"
        )
    }
  }

  def getSourceStream(
    uri: URI,
    filter: URI => Boolean = _ => true,
    offset: Int = 0,
    limit: Int = -1,
    consultCache: Boolean = false
  ): Stream[Source] = {
    uri.getScheme match {
      case "s3" =>
        getS3ObjectStream(uri.getHost, uri.getPath)
          .filter(obj => filter(URI.create(s"s3://${uri.getHost}/${obj.key}")))
          .map(obj => {
            logger.info(s"Pulling s3://${uri.getHost}/${obj.key()}")
            getS3Object(uri.getHost, obj.key(), consultCache)
          })
          .drop(offset)
          .safeTake(limit)
      case "file" =>
        val file = new File(uri)
        if (file.isDirectory) {
          getFilePathStream(uri, 1, filter, offset, limit)
            .map(path => {
              logger.info(s"Reading file://${path.toAbsolutePath.toString}")
              Source.fromFile(path.toFile)
            })
        } else {
          Stream(Source.fromFile(file))
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }

  def getSourceAsyncStream(
    uri: URI,
    filter: URI => Boolean = _ => true,
    offset: Int = 0,
    limit: Int = -1,
    consultCache: Boolean = false
  ): AsyncStream[Source] =
    AsyncStream.fromStream(
      getSourceStream(uri, filter, offset, limit, consultCache)
    )

  private def withRetries[T](
    maxAttempts: Int
  )(
    f: => T
  )(
    onError: PartialFunction[Throwable, Boolean] = { case NonFatal(_) => true }
  ): T = {
    @tailrec
    def withRetriesInternal(
      attempt: Int = 1,
      lastErr: Option[Throwable] = None
    ): T = {
      if (attempt > maxAttempts) {
        throw new RuntimeException(
          s"Giving up after $maxAttempts.",
          lastErr.orNull
        )
      }

      Try(f) match {
        case Failure(exception) if onError.isDefinedAt(exception) =>
          withRetriesInternal(attempt + 1, Some(exception))

        case Failure(exception) => throw exception

        case Success(value) =>
          value
      }
    }

    withRetriesInternal()
  }

  private def getS3ObjectStream(
    bucket: String,
    path: String
  ): Stream[S3Object] = {
    val sanitizedPath = path.stripPrefix("/")
    withRetries(5) {
      s3.listObjectsV2Paginator(
        ListObjectsV2Request
          .builder()
          .bucket(bucket)
          .prefix(sanitizedPath)
          .build()
      )
    } {
      case e: SdkClientException if e.retryable() =>
        true

      case NonFatal(_) =>
        logger.error(
          s"Failed to get object: s3://${bucket}/${sanitizedPath}"
        )
        true
    }.iterator()
      .asScala
      .toStream
      .flatMap(_.contents().asScala.toStream)
  }

  def getFilePathStream(
    baseUri: URI,
    depth: Int,
    filter: URI => Boolean,
    offset: Int,
    limit: Int
  ) = {
    Files
      .find(
        Paths.get(baseUri),
        depth,
        (path, attrs) =>
          !attrs.isDirectory && !path.getFileName.toString
            .contains(".DS_Store") && filter(path.toUri)
      )
      .toScala[Stream]
      .sortBy(_.toUri)
      .drop(offset)
      .safeTake(limit)
  }
}
