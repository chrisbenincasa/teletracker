package com.teletracker.tasks.scraper

import com.teletracker.common.model.scraping.amazon.AmazonItem
import com.teletracker.common.model.scraping.{
  MatchResult,
  PotentialMatch,
  ScrapeItemType,
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
    scrapeItemType: ScrapeItemType
  ): Stream[Either[Exception, MatchResult[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        parser
          .stream[MatchResult[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeItemType.HboCatalog =>
        parser
          .stream[MatchResult[HboScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.NetflixCatalog =>
        parser
          .stream[MatchResult[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.DisneyPlusCatalog =>
        parser
          .stream[MatchResult[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeItemType.HboMaxCatalog =>
        parser
          .stream[MatchResult[HboMaxScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.HboChanges =>
        parser
          .stream[MatchResult[HboScrapeChangesItem]](source.getLines())
      case ScrapeItemType.NetflixOriginalsArriving =>
        parser
          .stream[MatchResult[NetflixOriginalScrapeItem]](source.getLines())
      case ScrapeItemType.AmazonVideo =>
        parser.stream[MatchResult[AmazonItem]](source.getLines())

    }
  }

  def getPotentialMatchResultStream(
    source: Source,
    scrapeItemType: ScrapeItemType
  ): Stream[Either[Exception, PotentialMatch[_ <: ScrapedItem]]] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        parser
          .stream[PotentialMatch[HuluScrapeCatalogItem]](source.getLines())
      case ScrapeItemType.HboCatalog =>
        parser
          .stream[PotentialMatch[HboScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.NetflixCatalog =>
        parser
          .stream[PotentialMatch[NetflixScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.DisneyPlusCatalog =>
        parser
          .stream[PotentialMatch[DisneyPlusCatalogItem]](source.getLines())
      case ScrapeItemType.HboMaxCatalog =>
        parser
          .stream[PotentialMatch[HboMaxScrapedCatalogItem]](source.getLines())
      case ScrapeItemType.HboChanges =>
        parser
          .stream[PotentialMatch[HboScrapeChangesItem]](source.getLines())
      case ScrapeItemType.NetflixOriginalsArriving =>
        parser
          .stream[PotentialMatch[NetflixOriginalScrapeItem]](source.getLines())
      case ScrapeItemType.AmazonVideo =>
        parser.stream[PotentialMatch[AmazonItem]](source.getLines())
    }
  }

  def getPotentialMatchResultWithParsedStream(
    source: Source,
    scrapeItemType: ScrapeItemType
  ): Stream[
    Either[Exception, TypeWithParsedJson[PotentialMatch[_ <: ScrapedItem]]]
  ] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HuluScrapeCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HboScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.NetflixCatalog =>
        parser
          .streamWithParsed[PotentialMatch[NetflixScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.DisneyPlusCatalog =>
        parser
          .streamWithParsed[PotentialMatch[DisneyPlusCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboMaxCatalog =>
        parser
          .streamWithParsed[PotentialMatch[HboMaxScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboChanges =>
        parser
          .streamWithParsed[PotentialMatch[HboScrapeChangesItem]](
            source.getLines()
          )
      case ScrapeItemType.NetflixOriginalsArriving =>
        parser
          .streamWithParsed[PotentialMatch[NetflixOriginalScrapeItem]](
            source.getLines()
          )
      case ScrapeItemType.AmazonVideo =>
        parser.streamWithParsed[PotentialMatch[AmazonItem]](source.getLines())
    }
  }
}
