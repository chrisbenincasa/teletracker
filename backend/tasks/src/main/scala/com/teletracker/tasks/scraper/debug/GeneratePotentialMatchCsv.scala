package com.teletracker.tasks.scraper.debug

import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.model.scraping.hbo.{
  HboMaxCatalogItem,
  HboScrapedCatalogItem
}
import com.teletracker.common.model.scraping.{
  MatchResult,
  PotentialMatch,
  ScrapeItemType,
  ScrapedItem
}
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.model.scraping.netflix.{
  NetflixOriginalScrapeItem,
  NetflixScrapedCatalogItem
}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.scraper.hbo.HboScrapeChangesItem
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.io.Source

class GeneratePotentialMatchCsv extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val scrapeItemType = rawArgs.valueOrThrow[ScrapeItemType]("type")

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
      getStream(source, scrapeItemType)
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

  private def getStream(
    source: Source,
    scrapeItemType: ScrapeItemType
  ): Stream[Either[Exception, PotentialMatch[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        new IngestJobParser()
          .stream[PotentialMatch[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeItemType.HboCatalog =>
        new IngestJobParser()
          .stream[PotentialMatch[HboScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.NetflixCatalog =>
        new IngestJobParser()
          .stream[PotentialMatch[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.DisneyPlusCatalog =>
        new IngestJobParser()
          .stream[PotentialMatch[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeItemType.HboMaxCatalog =>
        new IngestJobParser()
          .stream[PotentialMatch[HboMaxCatalogItem]](source.getLines())
      case ScrapeItemType.HboChanges =>
        new IngestJobParser()
          .stream[PotentialMatch[HboScrapeChangesItem]](source.getLines())
      case ScrapeItemType.NetflixOriginalsArriving =>
        new IngestJobParser()
          .stream[PotentialMatch[NetflixOriginalScrapeItem]](source.getLines())
    }
  }
}

class GenerateMatchCsv extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val scrapeItemType = rawArgs.valueOrThrow[ScrapeItemType]("type")

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
      getStream(Source.fromURI(input), scrapeItemType)
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

  private def getStream(
    source: Source,
    scrapeItemType: ScrapeItemType
  ): Stream[Either[Exception, MatchResult[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        new IngestJobParser()
          .stream[MatchResult[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeItemType.HboCatalog =>
        new IngestJobParser()
          .stream[MatchResult[HboScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.NetflixCatalog =>
        new IngestJobParser()
          .stream[MatchResult[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.DisneyPlusCatalog =>
        new IngestJobParser()
          .stream[MatchResult[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeItemType.HboMaxCatalog =>
        new IngestJobParser()
          .stream[MatchResult[HboMaxCatalogItem]](source.getLines())
      case ScrapeItemType.HboChanges =>
        new IngestJobParser()
          .stream[MatchResult[HboScrapeChangesItem]](source.getLines())
      case ScrapeItemType.NetflixOriginalsArriving =>
        new IngestJobParser()
          .stream[MatchResult[NetflixOriginalScrapeItem]](source.getLines())
    }
  }
}
