package com.teletracker.tasks.util

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import scala.collection.JavaConverters._
import org.scalatest.flatspec.AnyFlatSpec

class SourceRetrieverTest extends AnyFlatSpec {
  it should "work" in {
    val s3 = S3Client.create()

    s3.listObjectsV2Paginator(
        ListObjectsV2Request
          .builder()
          .bucket("teletracker-data")
          .prefix(
            "scrape-results/netflix/new-on-netflix/2019-11-27/"
          )
          .build()
      )
      .iterator()
      .asScala
      .toStream
      .flatMap(_.contents().asScala.toStream)
      .foreach(obj => println(obj.key()))
  }
}
