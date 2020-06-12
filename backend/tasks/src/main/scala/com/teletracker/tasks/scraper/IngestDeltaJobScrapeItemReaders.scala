package com.teletracker.tasks.scraper

import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.util.{AsyncStream, Folds, OnceT}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import scala.io.Source
import scala.util.control.NonFatal

trait IngestDeltaJobScrapeItemReaders[
  IncomingType,
  ScrapeItemType <: ScrapedItem,
  ArgsType <: IngestJobArgsLike with IngestDeltaJobArgsLike] {
  self: IngestDeltaJobLike[IncomingType, ScrapeItemType, ArgsType] =>

  override protected def getAfterIds(): Set[String] = readAfterIdsOnce()

  override protected def getAllAfterItems(): List[ScrapeItemType] =
    readAfterItems()

  protected def getAddedItems(): List[PendingAvailabilityAdd] = {
    val afterItems = getAllAfterItems()
    val newIds = getAddedIds()
    val networks = getNetworksOrExit()

    AsyncStream
      .fromSeq(afterItems)
      .filter(item => containsUniqueKey(newIds, item))
      .throughApply(processAll(_, networks))
      .map {
        case (matchResults, nonMatches) =>
          val filteredResults = matchResults.filter {
            case MatchResult(_, esItem) =>
              args.itemIdFilter.forall(_ == esItem.id)
          }

          filteredResults -> nonMatches
      }
      .foldLeft(Folds.list2Empty[MatchResult[ScrapeItemType], ScrapeItemType])(
        Folds.fold2Append
      )
      .await()
      .withEffect {
        case (_, notFound) =>
          if (notFound.nonEmpty) {
            logger.warn(
              s"Could not find matches for added items: $notFound"
            )
          }
      }
      ._1
      .filter {
        case MatchResult(scrapedItem, esItem) =>
          args.itemIdFilter.forall(_ == esItem.id) &&
            uniqueKeyForIncoming(scrapedItem).exists(
              id => args.externalIdFilter.forall(_ == id)
            )
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          val newAvailabilities = createDeltaAvailabilities(
            networks,
            esItem,
            scrapedItem,
            isAvailable = true
          )
          PendingAvailabilityAdd(esItem, Some(scrapedItem), newAvailabilities)
      }
  }

  private[this] lazy val readAfterItems = OnceT {
    val afterItemSource =
      deps.sourceRetriever.getSource(args.snapshotAfter, consultCache = true)

    val items = readItems(afterItemSource)
    afterItemSource.close()
    items
  }

  private[this] lazy val readAfterIdsOnce = OnceT {
    deps.fileUtils
      .readAllLinesToUniqueIdSet[ScrapeItemType](
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
