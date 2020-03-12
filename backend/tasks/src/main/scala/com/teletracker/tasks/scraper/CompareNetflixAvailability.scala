package com.teletracker.tasks.scraper

import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import io.circe.generic.JsonCodec
import io.circe.syntax._
import io.circe._
import io.circe.parser._
import javax.inject.Inject
import java.net.URI
import java.nio.file.{Files, Paths}
import scala.compat.java8.StreamConverters._
import scala.io.Source

class CompareNetflixAvailability @Inject()()
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val whatsOnNetflixDir = args.valueOrThrow[URI]("whatsOnNetflixDir")
    val newOnNetflixDir = args.valueOrThrow[URI]("newOnNetflixDir")

    val allWhatsOnNetflixIds = Files
      .walk(Paths.get(whatsOnNetflixDir))
      .toScala[Stream]
      .filter(Files.isRegularFile(_))
      .foldLeft(Set.empty[String]) {
        case (acc, path) =>
          val src = Source.fromFile(path.toFile)
          try {
            acc ++ src
              .getLines()
              .flatMap(line => {
                decode[List[CompareNetflixAvailability_WhatsOnNetflixItem]](
                  line
                ).toOption
                  .map(_.map(_.netflixid))
                  .getOrElse(Nil)
              })
          } finally {
            src.close()
          }
      }

    val allNewOnNetflixIds = Files
      .walk(Paths.get(newOnNetflixDir))
      .toScala[Stream]
      .filter(Files.isRegularFile(_))
      .foldLeft(Set.empty[String]) {
        case (acc, path) =>
          val src = Source.fromFile(path.toFile)
          try {
            acc ++ src
              .getLines()
              .flatMap(line => {
                decode[CompareNetflixAvailability_NewOnNetflixItem](line).toOption
                  .map(_.externalId)
              })
          } finally {
            src.close()
          }
      }

    println("Missing from whatsOnNetflix:")
    println(allNewOnNetflixIds -- allWhatsOnNetflixIds)

    println("Missing from newOnNetflix:")
    println(allWhatsOnNetflixIds -- allNewOnNetflixIds)
  }
}

@JsonCodec
case class CompareNetflixAvailability_WhatsOnNetflixItem(netflixid: String)

@JsonCodec
case class CompareNetflixAvailability_NewOnNetflixItem(externalId: String)
