package com.teletracker.tasks.scraper

import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.model.scraping.{
  MatchResult,
  PartialEsItem,
  ScrapedItem
}
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{AsyncStream, Folds, OnceT}
import io.circe.Codec
import io.circe.syntax._
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import scala.util.control.NonFatal
import scala.concurrent.duration._

abstract class IngestDeltaJob[ScrapeItemType <: ScrapedItem](
  deps: IngestDeltaJobDependencies
)(implicit codec: Codec[ScrapeItemType],
  typedArgs: JsonableArgs[IngestDeltaJobArgs])
    extends IngestDeltaJobLike[
      ScrapeItemType,
      ScrapeItemType,
      IngestDeltaJobArgs
    ](deps)
    with IngestDeltaJobScrapeFileReaders[
      ScrapeItemType,
      ScrapeItemType
    ] {

  override def preparseArgs(args: RawArgs): IngestDeltaJobArgs = parseArgs(args)

  private def parseArgs(args: Map[String, Any]): IngestDeltaJobArgs = {
    IngestDeltaJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.value[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      itemIdFilter = args.value[UUID]("itemIdFilter"),
      sleepBetweenWriteMs = args.value[Long]("perBatchSleepMs"),
      processBatchSleep = args.value[Long]("perBatchSleepMs").map(_ millis),
      deltaSizeThreshold =
        args.valueOrDefault[Double]("deltaSizeThreshold", 5.0),
      disableDeltaSizeCheck =
        args.valueOrDefault("disableDeltaSizeCheck", false),
      externalIdFilter = args.value[String]("externalIdFilter"),
      parallelism = args.value[Int]("parallelism")
    )
  }

  override def runInternal(): Unit = {
    val networks = getNetworksOrExit()

    val afterIds = getAfterIds()
    val beforeIds = getBeforeIds()

    logger.info(s"Found ${afterIds.size} IDs in the current snapshot")
    logger.info(s"Found ${beforeIds.size} IDs in the previous snapshot")

    val newIds = afterIds -- beforeIds
    val removedIds = beforeIds -- afterIds
    val matchingIds = afterIds.intersect(beforeIds)

    val pctChange = Math.abs(
      (beforeIds.size - afterIds.size) / beforeIds.size.toDouble
    ) * 100.0

    if (args.snapshotBefore.nonEmpty && pctChange >= args.deltaSizeThreshold && !args.disableDeltaSizeCheck) {
      handleDiffEarlyExit(newIds = newIds, removedIds = removedIds)

      throw new RuntimeException(
        s"Delta ($pctChange)% exceeded configured threshold of ${args.deltaSizeThreshold}. Before: ${beforeIds.size}, After: ${afterIds.size}"
      )
    }

    logger.info(
      s"Checking for ${newIds.size} new IDs, ${removedIds.size} removed IDs, and ${matchingIds.size} matching IDs."
    )

    val afterItems = getAllAfterItems()

    val newAvailabilities = getAddedItems()

    val beforeItems = getAllBeforeItems()
    val removedAvailabilities = getRemovedItems()

    val beforeItemsById =
      beforeItems
        .flatMap(item => uniqueKeyForIncoming(item).map(_ -> item))
        .toMap
    val afterItemsById =
      afterItems
        .flatMap(item => uniqueKeyForIncoming(item).map(_ -> item))
        .toMap

    val itemChangesById = matchingIds.toSeq
      .map(id => {
        val before = beforeItemsById(id)
        val after = afterItemsById(id)

        before -> after
      })
      .flatMap {
        case (before, after) => processItemChange(before, after).toSeq
      }
      .map(change => {
        uniqueKeyForIncoming(change.after) -> change
      })
      .toMap

    val (changedMatches, changedNotFound) = AsyncStream
      .fromSeq(itemChangesById.values.toSeq)
      .map(_.after)
      .throughApply(processAll(_, networks))
      .map(
        matchesAndMisses =>
          filterMatchResults(
            matchesAndMisses._1,
            matchesAndMisses._2
          )
      )
      .foldLeft(Folds.list2Empty[MatchResult[ScrapeItemType], ScrapeItemType])(
        Folds.fold2Append
      )
      .await()

    val (updateChanges, removeChanges) = changedMatches
      .filter {
        case MatchResult(_, esItem) =>
          args.itemIdFilter.forall(_ == esItem.id)
      }
      .foldLeft(
        Folds.list2Empty[PendingAvailabilityUpdate, PendingAvailabilityRemove]
      ) {
        case ((adds, removes), MatchResult(scrapedItem, esItem)) =>
          itemChangesById.get(uniqueKeyForIncoming(scrapedItem)) match {
            case Some(ItemChange(_, _, ItemChangeUpdate)) =>
              val availabilities = createDeltaAvailabilities(
                networks,
                esItem,
                scrapedItem,
                isAvailable = true
              )

              (adds :+ PendingAvailabilityUpdate(
                esItem,
                Some(scrapedItem),
                availabilities
              )) -> removes
            case Some(ItemChange(_, _, ItemChangeRemove)) =>
              adds -> (removes :+ PendingAvailabilityRemove(
                esItem,
                createAvailabilityKeys(networks),
                uniqueKeyForIncoming(scrapedItem)
              ))
            case None => adds -> removes
          }
      }

    if (changedNotFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for changed items: $changedNotFound"
      )
    }

    logger.info(
      s"Found ${(newAvailabilities ++ updateChanges).flatMap(_.availabilities).size} availabilities to add/update"
    )

    logger.info(
      s"Found ${(removedAvailabilities ++ removeChanges).flatMap(_.removes).size} availabilities to remove"
    )

    val allChanges = newAvailabilities ++ updateChanges ++ removedAvailabilities ++ removeChanges
    if (!args.dryRun) {
      logger.info(
        s"Saving ${allChanges.size} availabilities"
      )

      saveExternalIdMappings(allChanges)
        .flatMap(_ => {
          saveAvailabilities(
            allChanges,
            shouldRetry = true
          )
        })
        .await()
    } else {
      writeChangesFile(allChanges)
    }
  }

  protected def handleDiffEarlyExit(
    newIds: Set[String],
    removedIds: Set[String]
  ): Unit = {
    val beforeItems = getAllBeforeItems()
    val afterItems = getAllAfterItems()

    val addedItems = afterItems
      .filter(item => containsUniqueKey(newIds, item))

    val removedItems = beforeItems
      .filter(item => containsUniqueKey(removedIds, item))

    val today = LocalDate.now()
    val changes = new File(
      s"${today}_${getClass.getSimpleName}-changes-error.json"
    )
    val changesPrinter = new PrintWriter(
      new BufferedOutputStream(new FileOutputStream(changes))
    )

    addedItems.foreach(item => {
      changesPrinter.println(
        Map(
          "change_type" -> "added".asJson,
          "item" -> item.asJson
        ).asJson.noSpaces
      )
    })

    removedItems.foreach(item => {
      changesPrinter.println(
        Map(
          "change_type" -> "removed".asJson,
          "item" -> item.asJson
        ).asJson.noSpaces
      )
    })

    changesPrinter.flush()
    changesPrinter.close()
  }

  override protected def uniqueKeyForExisting(
    item: ScrapeItemType
  ): Option[String] = uniqueKeyForIncoming(item)

  private def filterMatchResults(
    matches: List[MatchResult[ScrapeItemType]],
    nonMatches: List[ScrapeItemType]
  ): (List[MatchResult[ScrapeItemType]], List[ScrapeItemType]) = {
    val filteredResults = matches.filter {
      case MatchResult(_, esItem) =>
        args.itemIdFilter.forall(_ == esItem.id)
    }

    filteredResults -> nonMatches
  }

  protected def getRemovedItems(): List[PendingAvailabilityRemove] = {
    val beforeItems = getAllBeforeItems()
    val removedIds = getRemovedIds()
    val networks = getNetworksOrExit()

    val (found, notFound) = AsyncStream
      .fromSeq(beforeItems)
      .filter(item => containsUniqueKey(removedIds, item))
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

    if (notFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for removed items: $notFound"
      )
    }

    found
      .filter {
        case MatchResult(_, esItem) =>
          args.itemIdFilter.forall(_ == esItem.id)
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          PendingAvailabilityRemove(
            esItem,
            createAvailabilityKeys(networks),
            uniqueKeyForIncoming(scrapedItem)
          )
      }
  }

  override protected def getBeforeIds(): Set[String] = readBeforeIdsOnce()

  override protected def getAllBeforeItems(): List[ScrapeItemType] =
    readBeforeItems()

  private[this] val readBeforeIdsOnce = OnceT {
    args.snapshotBefore
      .map(
        deps.fileUtils
          .readAllLinesToUniqueIdSet[ScrapeItemType](
            _,
            uniqueKeyForIncoming,
            consultSourceCache = true
          )
          .filter(id => {
            args.externalIdFilter.forall(_ == id)
          })
      )
      .getOrElse(Set.empty[String])
  }

  private[this] val readBeforeItems = OnceT {
    args.snapshotBefore
      .map(before => {
        val beforeItemSource =
          deps.sourceRetriever.getSource(before, consultCache = true)

        readItems(beforeItemSource)
      })
      .getOrElse(Nil)
  }

  override protected def writePotentialMatches(
    potentialMatches: Iterable[(EsItem, ScrapeItemType)]
  ): Unit = {
    import UpdateableEsItem.syntax._

    super.writePotentialMatches(potentialMatches)

    val writePotentialMatchesToEs = rawArgs.valueOrDefault(
      "writePotentialMatchesToEs",
      false
    )

    if (!args.dryRun || writePotentialMatchesToEs) {
      val networks = getNetworksOrExit()

      val items = for {
        (item, t) <- potentialMatches.toList
        externalId <- t.externalId.toList
      } yield {
        val esExternalId = EsExternalId(externalSource, externalId)
        val now = OffsetDateTime.now()
        val insert = EsPotentialMatchItem(
          id = EsPotentialMatchItem.id(item.id, esExternalId),
          created_at = now,
          state = EsPotentialMatchState.Unmatched,
          last_updated_at = now,
          last_state_change = now,
          potential = PartialEsItem.forEsItem(item),
          scraped = EsGenericScrapedItem(
            scrapeItemType,
            EsScrapedItem.fromAnyScrapedItem(t),
            t.asJson
          ),
          availability = Some(createAvailabilities(networks, item, t).toList)
        )

        val update: EsPotentialMatchItemUpdateView = insert.toUpdateable

        update.copy(state = None, last_state_change = None) -> insert
      }

      deps.esPotentialMatchItemStore
        .upsertBatchWithFallback(items)
        .recover {
          case NonFatal(e) =>
            logger.warn(
              "Failed while attempting to write potential matches to ES.",
              e
            )
        }
        .await()
    }
  }
}
