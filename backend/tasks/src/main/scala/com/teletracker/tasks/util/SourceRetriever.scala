package com.teletracker.tasks.util

import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  ListObjectsV2Request,
  NoSuchKeyException
}
import software.amazon.awssdk.utils.IoUtils
import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class SourceRetriever @Inject()(s3: S3Client) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val localFsCache = new ConcurrentHashMap[String, URI]()

  def getSource(
    uri: URI,
    consultCache: Boolean = false
  ): Source = {
    uri.getScheme match {
      case "s3" =>
        getS3Object(uri.getHost, uri.getPath, consultCache)

      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupposed file scheme: ${uri.getScheme}"
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

  def getSourceStream(
    uri: URI,
    consultCache: Boolean = false
  ): Stream[Source] = {
    uri.getScheme match {
      case "s3" =>
        withRetries(5) {
          s3.listObjectsV2Paginator(
            ListObjectsV2Request
              .builder()
              .bucket(uri.getHost)
              .prefix(uri.getPath.stripPrefix("/"))
              .build()
          )
        } {
          case e: SdkClientException if e.retryable() =>
            true

          case NonFatal(e) =>
            logger.error(
              s"Failed to get object: s3://${uri.getHost}/${uri.getPath.stripPrefix("/")}"
            )
            true
        }.iterator()
          .asScala
          .toStream
          .flatMap(_.contents().asScala.toStream)
          .map(obj => {
            logger.info(s"Pulling s3://${uri.getHost}/${obj.key()}")
            getS3Object(uri.getHost, obj.key(), consultCache)
          })
      case "file" =>
        Stream(Source.fromFile(uri))
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }

  def withRetries[T](
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
}
