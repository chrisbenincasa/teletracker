package com.teletracker.tasks.util

import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  GetObjectResponse,
  ListObjectsV2Request
}
import software.amazon.awssdk.utils.IoUtils
import java.net.URI
import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.io.{BufferedSource, Source}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class SourceRetriever @Inject()(s3: S3Client) {
  private val logger = LoggerFactory.getLogger(getClass)

  def getSource(uri: URI): Source = {
    uri.getScheme match {
      case "s3" =>
        getS3Object(uri.getHost, uri.getPath)

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
    key: String
  ): Source = {
    val stream = withRetries(5) {
      s3.getObject(
        GetObjectRequest
          .builder()
          .bucket(bucket)
          .key(key.stripPrefix("/"))
          .build()
      )
    } {
      case e: SdkClientException if e.retryable() =>
        true

      case NonFatal(e) =>
        logger.error(
          s"Failed to get object: s3://${bucket}/${key.stripPrefix("/")}",
          e
        )

        true
    }

    val finalStream = stream.response().contentEncoding() match {
      case "gzip" =>
        new GZIPInputStream(
          stream
        )

      case _ => stream
    }

    // not ideal to suck this all into memory but we keep getting SocketExceptions with Connection rest
    // an hour into jobs sooooo what are we supposed to do???
    Source.fromBytes(IoUtils.toByteArray(finalStream))
  }

  def getSourceStream(uri: URI): Stream[Source] = {
    uri.getScheme match {
      case "s3" =>
        val allEntries = withRetries(5) {
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
          .map(obj => uri.getHost -> obj.key())
          .toList

        allEntries.toStream.map {
          case (bucket, key) => getS3Object(bucket, key)
        }
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
