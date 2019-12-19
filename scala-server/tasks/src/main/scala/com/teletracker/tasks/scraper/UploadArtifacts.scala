package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

trait UploadArtifacts { self: BaseIngestJob[_, _] =>
  protected def teletrackerConfig: TeletrackerConfig
  protected def s3: S3Client

  postrun { (args: self.TypedArgs) =>
    artifacts.foreach(artifact => {
      s3.putObject(
        PutObjectRequest
          .builder()
          .bucket(teletrackerConfig.data.s3_bucket)
          .key(
            s"task-output/${getClass.getSimpleName}/$now/${artifact.getName}"
          )
          .build(),
        artifact.toPath
      )
    })
  }
}
