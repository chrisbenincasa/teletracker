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

  def readAllLinesToSet(
    loc: URI,
    consultSourceCache: Boolean
  ): Set[String] = {
    readAllLinesToUniqueIdSet[String, String](
      loc,
      Some(_),
      consultSourceCache = consultSourceCache
    )
  }

  def readAllLinesToUniqueIdSet[T: Decoder, U](
    loc: URI,
    uniqueId: T => Option[U],
    consultSourceCache: Boolean
  ): Set[U] = {
    sourceRetriever
      .getSourceStream(loc, consultCache = consultSourceCache)
      .foldLeft(Set.empty[U]) {
        case (set, src) =>
          try {
            ingestJobParser
              .get()
              .stream[T](src.getLines())
              .flatMap {
                case Left(NonFatal(ex)) =>
                  logger.warn(s"Error parsing line: ${ex.getMessage}")
                  None
                case Right(value) => uniqueId(value)
              }
              .foldLeft(set)(_ + _)
          } finally {
            src.close()
          }
      }
  }
}
