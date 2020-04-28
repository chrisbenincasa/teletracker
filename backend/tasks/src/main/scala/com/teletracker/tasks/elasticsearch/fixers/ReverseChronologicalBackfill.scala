package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.model.tmdb.{Movie, Person, TmdbError, TvShow}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import io.circe.Codec
import io.circe.syntax._
import io.circe.parser._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

abstract class ReverseChronologicalBackfill[T: Codec]
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")
    val regionString = args.valueOrDefault("region", "us-west-2")
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault[Int]("limit", -1)
    val perFileLimit = args.valueOrDefault[Int]("perFileLimit", -1)
    val append = args.valueOrDefault[Boolean]("append", false)
    val gteFilter = args.value[String]("gteFilter")
    val ltFilter = args.value[String]("ltFilter")
    val baseFileName = args.valueOrThrow[String]("baseFileName")
    val outputFolder = args.valueOrThrow[String]("outputFolder")

    val region = Region.of(regionString)

    val s3 = S3Client.builder().region(region).build()

    val retriever = new SourceRetriever(s3)

    val fileRotator = FileRotator.everyNBytes(
      baseFileName,
      StorageUnit.fromMegabytes(100),
      Some(outputFolder),
      append = append
    )

    def filter(uri: URI) = {
      lazy val sanitized = uri.getPath.stripPrefix("/")
      gteFilter.forall(f => sanitized >= f) &&
      ltFilter.forall(f => sanitized < f)
    }

    val seen = ConcurrentHashMap.newKeySet[String]()

    // Add any existing ids in the target directory
    retriever
      .getUriStream(fileRotator.baseUri)
      .map(retriever.getSource(_))
      .foreach(source => {
        try {
          new IngestJobParser()
            .stream[T](source.getLines())
            .collect {
              case Right(value) => value
            }
            .map(uniqueId)
            .foreach(seen.add)
        } finally {
          source.close()
        }
      })

    logger.info(s"Added ${seen.size()} items to the seen set.")

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
            .stream[T](source.getLines())
            .flatMap {
              case Left(value) =>
                logger.error(s"Could not parse line: ${value.getMessage}")
                None
              case Right(value) if seen.add(uniqueId(value)) => Some(value)
              case _                                         => None
            }
            .safeTake(perFileLimit)
            .foreach(person => {
              fileRotator.writeLines(Seq(person.asJson.noSpaces))
            })
        } finally {
          source.close()
        }
      })

    fileRotator.finish()
  }

  protected def uniqueId(item: T): String
}

class PersonReverseChronologicalBackfill
    extends ReverseChronologicalBackfill[Person] {
  override protected def uniqueId(item: Person): String = item.id.toString
}

class MovieReverseChronologicalBackfill
    extends ReverseChronologicalBackfill[Movie] {
  override protected def uniqueId(item: Movie): String = item.id.toString
}

class TvShowReverseChronologicalBackfill
    extends ReverseChronologicalBackfill[TvShow] {
  override protected def uniqueId(item: TvShow): String = item.id.toString
}
