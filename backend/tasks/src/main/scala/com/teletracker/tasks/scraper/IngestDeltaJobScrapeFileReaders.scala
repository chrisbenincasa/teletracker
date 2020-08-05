package com.teletracker.tasks.scraper

import com.teletracker.common.model.scraping.ScrapedItem
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.OnceT
import scala.io.Source
import scala.util.control.NonFatal

trait IngestDeltaJobScrapeFileReaders[
  IncomingType,
  ScrapeItemType <: ScrapedItem] {
  self: IngestDeltaJobLike[IncomingType, ScrapeItemType, IngestDeltaJobArgs] =>

  override protected def getAfterIds(): Set[String] = readAfterIdsOnce()

  override protected def getAllAfterItems(): List[ScrapeItemType] =
    readAfterItems()

  private[this] lazy val readAfterItems = OnceT {
    val afterItemSource =
      deps.sourceRetriever.getSource(args.snapshotAfter, consultCache = true)

    val items = readItems(afterItemSource)
    afterItemSource.close()
    items
  }

  private[this] lazy val readAfterIdsOnce = OnceT {
    deps.sourceUtils
      .readAllLinesToUniqueIdSet[ScrapeItemType, String](
        args.snapshotAfter,
        uniqueKeyForIncoming,
        consultSourceCache = true
      )
      .filter(id => {
        args.externalIdFilter.forall(_ == id)
      })
  }

  protected def readItems(source: Source): List[ScrapeItemType] = {
    new IngestJobParser()
      .asyncStream[ScrapeItemType](source.getLines())
      .flatMapOption {
        case Left(NonFatal(ex)) =>
          logger.warn(s"Error parsing line: ${ex.getMessage}")
          None

        case Right(value) =>
          Some(value)

        case _ => None
      }
      .toList
      .await()
  }
}
