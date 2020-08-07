package com.teletracker.tasks.scraper.debug

import com.teletracker.common.model.scraping.ScrapeCatalogType
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.tasks.scraper.ScrapeItemStreams
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.io.Source

class GeneratePotentialMatchCsv extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val scrapeItemType = rawArgs.valueOrThrow[ScrapeCatalogType]("type")

    val source = Source.fromURI(input)

    val basename = input.getPath.split("/").last
    val fileName = basename.substring(0, basename.lastIndexOf("."))
    val outputFile = new File(s"$fileName.csv")
    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(outputFile))
    )

    writer.println(
      List(
        "scraped_title",
        "potential_title",
        "potential_id",
        "external_id",
        "storage_link",
        "tt_link",
        "external_link"
      ).mkString(",")
    )

    val seen = ConcurrentHashMap.newKeySet[String]()

    try {
      ScrapeItemStreams
        .getPotentialMatchResultStream(source, scrapeItemType)
        .foreach {
          case Left(value) => logger.error("Could not parse line", value)
          case Right(value) =>
            if (seen.add(value.scraped.title)) {
              val year = value.potential.release_date
                .map(_.getYear)
                .map(_.toString)
                .getOrElse("")
              val scrapedYear =
                value.scraped.releaseYear.map(_.toString).getOrElse("")

              writer.println(
                List(
                  s""""${value.scraped.title} ($scrapedYear)"""",
                  s""""${value.potential.title} ($year)"""",
                  value.potential.id,
                  value.scraped.externalId.getOrElse(""),
                  s"https://search.internal.qa.teletracker.tv/items_live/_doc/${value.potential.id}",
                  s"https://qa.teletracker.tv/${value.potential.`type`}/${value.potential.id}",
                  value.scraped.url.getOrElse("")
                ).mkString(",")
              )
            }
        }
    } finally {
      source.close()
    }

    writer.flush()
    writer.close()
  }
}

class GenerateMatchCsv extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val scrapeItemType = rawArgs.valueOrThrow[ScrapeCatalogType]("type")

    val source = Source.fromURI(input)

    val basename = input.getPath.split("/").last
    val fileName = basename.substring(0, basename.lastIndexOf("."))
    val outputFile = new File(s"$fileName.csv")
    val writer = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(outputFile))
    )

    writer.println(
      List(
        "item_title",
        "scraped_title",
        "item_id",
        "storage_link",
        "external_link"
      ).mkString(",")
    )

    val seen = ConcurrentHashMap.newKeySet[String]()

    try {
      ScrapeItemStreams
        .getMatchResultStream(Source.fromURI(input), scrapeItemType)
        .foreach {
          case Left(value) => logger.error("Could not parse line", value)
          case Right(value) =>
            if (seen.add(value.scrapedItem.title)) {
              val year = value.esItem.release_date
                .map(_.getYear)
                .map(_.toString)
                .getOrElse("")
              val scrapedYear =
                value.scrapedItem.releaseYear.map(_.toString).getOrElse("")
              writer.println(
                List(
                  s""""${value.esItem.title.get.headOption
                    .getOrElse("")} ($year)"""",
                  s""""${value.scrapedItem.title} ($scrapedYear)"""",
                  value.esItem.id,
                  s"https://search.internal.qa.teletracker.tv/items_live/_doc/${value.esItem.id}",
                  value.scrapedItem.url.getOrElse("")
                ).mkString(",")
              )
            }
        }
    } finally {
      source.close()
    }

    writer.flush()
    writer.close()
  }
}
