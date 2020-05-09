package com.teletracker.tasks.aws

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{
  CopyObjectRequest,
  GetObjectRequest,
  PutObjectTaggingRequest
}
import java.net.URI
import java.util

class FixS3Metadata @Inject()(
  s3Client: S3Client,
  retriever: SourceRetriever)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val bucket = args.valueOrThrow[String]("bucket")
    val path = args.valueOrThrow[String]("path")
    val gteFilter = args.value[String]("gteFilter")
    val ltFilter = args.value[String]("ltFilter")

    def filter(uri: URI) = {
      lazy val sanitized = uri.getPath.stripPrefix("/")
      gteFilter.forall(f => sanitized >= f) &&
      ltFilter.forall(f => sanitized < f)
    }

    retriever
      .getS3ObjectStream(bucket, path)
      .foreach(s3Object => {
        val metadata = s3Client
          .getObject(
            GetObjectRequest
              .builder()
              .bucket(bucket)
              .key(s3Object.key())
              .build()
          )
          .response()
          .metadata()

        val newMetadata = new util.HashMap[String, String](metadata)
        newMetadata.remove("Content-Encoding")

        s3Client.copyObject(
          CopyObjectRequest
            .builder()
            .bucket(bucket)
            .key(s3Object.key())
            .metadata(newMetadata)
            .build()
        )
      })
  }
}
