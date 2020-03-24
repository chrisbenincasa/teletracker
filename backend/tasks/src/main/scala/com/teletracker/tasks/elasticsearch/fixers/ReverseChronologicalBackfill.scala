package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.model.tmdb.Person
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.tasks.elasticsearch.FileRotator
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.tmdb.export_tasks.{
  PersonChangesDumpTask,
  PersonDumpFileRow
}
import com.teletracker.tasks.util.SourceRetriever
import com.twitter.util.StorageUnit
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.io.Source
import io.circe.syntax._

class ReverseChronologicalBackfill extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val regionString = args.valueOrDefault("region", "us-west-2")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault[Int]("limit", -1)
    val perFileLimit = args.valueOrDefault[Int]("perFileLimit", -1)
    val append = args.valueOrDefault[Boolean]("append", false)
    val region = Region.of(regionString)
    val gteFilter = args.value[String]("gteFilter")
    val ltFilter = args.value[String]("ltFilter")

    val s3 = S3Client.builder().region(region).build()

    val retriever = new SourceRetriever(s3)

    val fileRotator = FileRotator.everyNBytes(
      "people-backfill",
      StorageUnit.fromMegabytes(100),
      Some("people-backfill"),
      append = append
    )

    def filter(uri: URI) = {
      lazy val sanitized = uri.getPath.stripPrefix("/")
      gteFilter.forall(f => sanitized >= f) &&
      ltFilter.forall(f => sanitized < f)
    }

    val seen = ConcurrentHashMap.newKeySet[Int]()

    retriever
      .getUriStream(
        input,
        filter = filter,
        offset = offset,
        limit = limit
      )
      .reverse
      .map(uri => {
        logger.info(s"Pulling ${uri}")
        retriever.getSource(uri)
      })
      .foreach(source => {
        try {
          new IngestJobParser()
            .stream[Person](source.getLines())
            .collect {
              case Right(value) if seen.add(value.id) => value
            }
            .foreach(person => {
              fileRotator.writeLines(Seq(person.asJson.noSpaces))
            })
        } finally {
          source.close()
        }
      })

    fileRotator.finish()
  }
}
