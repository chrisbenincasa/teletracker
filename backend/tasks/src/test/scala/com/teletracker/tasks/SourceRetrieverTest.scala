package com.teletracker.tasks

import com.teletracker.tasks.util.SourceRetriever
import org.scalatest.FlatSpec
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, S3Object}
import java.net.URI
import scala.collection.JavaConverters._
import scala.io.Source

class SourceRetrieverTest extends FlatSpec {
  val s3 = S3Client.builder().region(Region.US_WEST_1).build()

  it should "test" in {
    getObj("data-dump" :: "movie-delta" :: Nil)
      .filter(_.key() >= "data-dump/movie-delta/2019-12-09")
      .take(5)
      .map(_.key())
      .foreach(println)
  }

  it should "sort" in {
    new SourceRetriever(s3)
      .getFilePathStream(
        URI.create(
          "file:///Users/christianbenincasa/Code/projects/teletracker/backend/tasks/people-backfill"
        ),
        1,
        _ => true,
        0,
        -1
      )
      .foreach(println)
  }

  private def getObj(prefix: List[String]): Stream[S3Object] = {
    s3.listObjectsV2Paginator(
        ListObjectsV2Request
          .builder()
          .bucket("teletracker-data")
          .prefix(prefix.mkString("/"))
          .build()
      )
      .asScala
      .toStream
      .flatMap(_.contents().asScala.toStream)
  }
}
