package com.teletracker.tasks.scraper

import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.model.scraping.{
  MatchResult,
  PotentialMatch,
  ScrapeCatalogType,
  ScrapedItem
}
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.model.scraping.hbo.{
  HboMaxScrapedCatalogItem,
  HboScrapedCatalogItem
}
import com.teletracker.common.model.scraping.hulu.HuluScrapeCatalogItem
import com.teletracker.common.model.scraping.netflix.{
  NetflixOriginalScrapeItem,
  NetflixScrapedCatalogItem
}
import com.teletracker.tasks.scraper.hbo.HboScrapeChangesItem
import scala.io.Source

object ScrapeItemStreams {
  private val parser = new IngestJobParser()

  def getMatchResultStream(
    source: Source,
    scrapeItemType: ScrapeCatalogType
  ): Stream[Either[Exception, MatchResult[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeCatalogType.HuluCatalog =>
        parser
          .stream[MatchResult[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboCatalog =>
        parser
          .stream[MatchResult[HboScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.NetflixCatalog =>
        parser
          .stream[MatchResult[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.DisneyPlusCatalog =>
        parser
          .stream[MatchResult[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboMaxCatalog =>
        parser
          .stream[MatchResult[HboMaxScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboChanges =>
        parser
          .stream[MatchResult[HboScrapeChangesItem]](source.getLines())
      case ScrapeCatalogType.NetflixOriginalsArriving =>
        parser
          .stream[MatchResult[NetflixOriginalScrapeItem]](source.getLines())
      case ScrapeCatalogType.AmazonVideo =>
        parser.stream[MatchResult[AmazonItem]](source.getLines())

    }
  }

  def getPotentialMatchResultStream(
    source: Source,
    scrapeItemType: ScrapeCatalogType
  ): Stream[Either[Exception, PotentialMatch[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeCatalogType.HuluCatalog =>
        parser
          .stream[PotentialMatch[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboCatalog =>
        parser
          .stream[PotentialMatch[HboScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.NetflixCatalog =>
        parser
          .stream[PotentialMatch[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.DisneyPlusCatalog =>
        parser
          .stream[PotentialMatch[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboMaxCatalog =>
        parser
          .stream[PotentialMatch[HboMaxScrapedCatalogItem]](source.getLines())
      case ScrapeCatalogType.HboChanges =>
        parser
          .stream[PotentialMatch[HboScrapeChangesItem]](source.getLines())
      case ScrapeCatalogType.NetflixOriginalsArriving =>
        parser
          .stream[PotentialMatch[NetflixOriginalScrapeItem]](source.getLines())
      case ScrapeCatalogType.AmazonVideo =>
        parser.stream[PotentialMatch[AmazonItem]](source.getLines())
    }
  }

  def getPotentialMatchResultWithParsedStream(
    source: Source,
    scrapeItemType: ScrapeCatalogType
  ): Stream[
    Either[Exception, TypeWithParsedJson[PotentialMatch[_ <: ScrapedItem]]]
  ] = {
    scrapeItemType match {
      case ScrapeCatalogType.HuluCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HuluScrapeCatalogItem]](
            source.getLines()
          )
      case ScrapeCatalogType.HboCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HboScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeCatalogType.NetflixCatalog =>
        parser
          .streamWithParsed[PotentialMatch[NetflixScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeCatalogType.DisneyPlusCatalog =>
        parser
          .streamWithParsed[PotentialMatch[DisneyPlusCatalogItem]](
            source.getLines()
          )
      case ScrapeCatalogType.HboMaxCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HboMaxScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeCatalogType.HboChanges =>
        parser
          .streamWithParsed[PotentialMatch[HboScrapeChangesItem]](
            source.getLines()
          )
      case ScrapeCatalogType.NetflixOriginalsArriving =>
        parser
          .streamWithParsed[PotentialMatch[NetflixOriginalScrapeItem]](
            source.getLines()
          )
      case ScrapeCatalogType.AmazonVideo =>
        parser.streamWithParsed[PotentialMatch[AmazonItem]](source.getLines())
    }
  }
}
