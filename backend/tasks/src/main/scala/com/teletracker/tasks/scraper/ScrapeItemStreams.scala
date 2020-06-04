package com.teletracker.tasks.scraper

import com.teletracker.common.model.scraping.{
  MatchResult,
  PotentialMatch,
  ScrapeItemType,
  ScrapedItem
}
import com.teletracker.common.model.scraping.disney.DisneyPlusCatalogItem
import com.teletracker.common.model.scraping.hbo.{
  HboMaxCatalogItem,
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
  def getMatchResultStream(
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

  def getPotentialMatchResultStream(
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

  def getPotentialMatchResultWithParsedStream(
    source: Source,
    scrapeItemType: ScrapeItemType
  ): Stream[
    Either[Exception, TypeWithParsedJson[PotentialMatch[_ <: ScrapedItem]]]
  ] = {
    scrapeItemType match {
      case ScrapeItemType.HuluCatalog =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[HuluScrapeCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboCatalog =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[HboScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.NetflixCatalog =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[NetflixScrapedCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.DisneyPlusCatalog =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[DisneyPlusCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboMaxCatalog =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[HboMaxCatalogItem]](
            source.getLines()
          )
      case ScrapeItemType.HboChanges =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[HboScrapeChangesItem]](
            source.getLines()
          )
      case ScrapeItemType.NetflixOriginalsArriving =>
        new IngestJobParser()
          .streamWithParsed[PotentialMatch[NetflixOriginalScrapeItem]](
            source.getLines()
          )
    }
  }
}
