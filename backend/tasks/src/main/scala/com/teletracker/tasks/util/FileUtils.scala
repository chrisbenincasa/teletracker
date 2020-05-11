package com.teletracker.tasks.util

import com.teletracker.tasks.scraper.IngestJobParser
import io.circe.{Codec, Decoder}
import javax.inject.{Inject, Provider}
import org.slf4j.LoggerFactory
import java.net.URI
import scala.util.control.NonFatal

class FileUtils @Inject()(
  sourceRetriever: SourceRetriever,
  ingestJobParser: Provider[IngestJobParser]) {
  private val logger = LoggerFactory.getLogger(getClass)

  def readAllLinesToSet(loc: URI): Set[String] = {
    readAllLinesToUniqueIdSet[String](loc, identity)
  }

  def readAllLinesToUniqueIdSet[T: Decoder](
    loc: URI,
    uniqueId: T => String
  ): Set[String] = {
    sourceRetriever.getSourceStream(loc).foldLeft(Set.empty[String]) {
      case (set, src) =>
        try {
          ingestJobParser
            .get()
            .stream[T](src.getLines())
            .flatMap {
              case Left(NonFatal(ex)) =>
                logger.warn(s"Error parsing line: ${ex.getMessage}")
                None
              case Right(value) => Some(uniqueId(value))
            }
            .foldLeft(set)(_ + _)
        } finally {
          src.close()
        }
    }
  }
}
