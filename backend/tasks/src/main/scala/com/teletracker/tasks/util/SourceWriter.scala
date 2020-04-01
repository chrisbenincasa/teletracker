package com.teletracker.tasks.util

import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

class SourceWriter @Inject()(s3Client: S3Client) {
  def writeFile(
    destination: URI,
    file: Path
  ): Unit = {
    destination.getScheme match {
      case "file" =>
        val dest = new File(destination)
        if (!dest.exists()) {
          dest.getParentFile.mkdirs()
          dest.createNewFile()
        }

        Files.copy(
          file,
          Paths.get(destination),
          StandardCopyOption.REPLACE_EXISTING
        )
      case "s3" =>
        s3Client.putObject(
          PutObjectRequest
            .builder()
            .bucket(destination.getHost)
            .key(destination.getPath.stripPrefix("/"))
            .build(),
          file
        )
      case x =>
        throw new IllegalArgumentException(
          s"Unsupported scheme: $x for dest: $destination"
        )
    }
  }
}
