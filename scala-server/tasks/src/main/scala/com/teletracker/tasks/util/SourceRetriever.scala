package com.teletracker.tasks.util

import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  GetObjectRequest,
  ListObjectsV2Request
}
import java.net.URI
import scala.io.Source
import scala.collection.JavaConverters._

class SourceRetriever @Inject()(s3: S3Client) {
  def getSource(uri: URI): Source = {
    uri.getScheme match {
      case "s3" =>
        Source.fromBytes(
          s3.getObjectAsBytes(
              GetObjectRequest
                .builder()
                .bucket(uri.getHost)
                .key(uri.getPath.stripPrefix("/"))
                .build()
            )
            .asByteArray()
        )
      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupposed file scheme: ${uri.getScheme}"
        )
    }
  }

  def getSourceStream(uri: URI) = {
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
            Source.fromBytes(
              s3.getObjectAsBytes(
                  GetObjectRequest
                    .builder()
                    .bucket(uri.getHost)
                    .key(obj.key())
                    .build()
                )
                .asByteArray()
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
}
