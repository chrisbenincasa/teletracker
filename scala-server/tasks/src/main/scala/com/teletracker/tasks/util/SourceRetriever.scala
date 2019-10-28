package com.teletracker.tasks.util

import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  ListObjectsV2Request
}
import java.net.URI
import java.util.zip.GZIPInputStream
import scala.io.{BufferedSource, Source}
import scala.collection.JavaConverters._
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
  ) = {
    val stream = try {
      s3.getObject(
        GetObjectRequest
          .builder()
          .bucket(bucket)
          .key(key.stripPrefix("/"))
          .build()
      )
    } catch {
      case NonFatal(e) =>
        logger.error(
          s"Failed to get object: s3://${bucket}/${key.stripPrefix("/")}"
        )
        throw e
    }

    val finalStream = stream.response().contentEncoding() match {
      case "gzip" =>
        new GZIPInputStream(
          stream
        )

      case _ => stream
    }

    Source.fromInputStream(finalStream)
  }

  def getSourceStream(uri: URI): Stream[BufferedSource] = {
    uri.getScheme match {
      case "s3" =>
        s3.listObjectsV2Paginator(
            ListObjectsV2Request
              .builder()
              .bucket(uri.getHost)
              .prefix(uri.getPath.stripPrefix("/"))
              .build()
          )
          .iterator()
          .asScala
          .toStream
          .flatMap(_.contents().asScala.toStream)
          .map(obj => {
            getS3Object(uri.getHost, obj.key())
          })
      case "file" =>
        Stream(Source.fromFile(uri))
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported file scheme: ${uri.getScheme}"
        )
    }
  }
}
