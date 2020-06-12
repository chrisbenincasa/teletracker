package com.teletracker.tasks.scraper

import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryBuilder,
  ItemsScroller
}
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.{Folds, OnceT}
import com.teletracker.common.util.Futures._
import io.circe.Codec
import org.elasticsearch.index.query.QueryBuilders
import java.net.URI
import java.util.UUID

abstract class LiveIngestDeltaJob[T <: ScrapedItem](
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller
)(implicit codec: Codec[T],
  typedArgs: JsonableArgs[IngestDeltaJobArgs])
    extends IngestDeltaJobLike[EsItem, T, IngestDeltaJobArgs](deps)
    with IngestDeltaJobScrapeItemReaders[EsItem, T, IngestDeltaJobArgs] {
  override def preparseArgs(args: RawArgs): IngestDeltaJobArgs = parseArgs(args)

  private def parseArgs(args: Map[String, Option[Any]]): IngestDeltaJobArgs = {
    IngestDeltaJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.value[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      itemIdFilter = args.value[UUID]("itemIdFilter"),
      externalIdFilter = args.value[String]("externalIdFilter"),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs"),
      deltaSizeThreshold =
        args.valueOrDefault[Double]("deltaSizeThreshold", 5.0),
      disableDeltaSizeCheck =
        args.valueOrDefault("disableDeltaSizeCheck", false)
    )
  }

  override def runInternal(): Unit = {
    val networks = getNetworksOrExit()
    val existingAvailability = itemsScroller
      .start(
        AvailabilityQueryBuilder.hasAvailabilityForNetworks(
          QueryBuilders.boolQuery(),
          networks
        )
      )
      .toList
      .await()

    val beforeItems = existingAvailability.filter(item => {
      networks.toList
        .map(_.id)
        .flatMap(item.availabilityGrouped.getOrElse(_, Nil))
        .nonEmpty
    })

    val beforeIds = beforeItems
      .flatMap(item => item.externalIdsGrouped.get(externalSource))
      .toSet
    val beforeItemsById = beforeItems
      .flatMap(
        item => item.externalIdsGrouped.get(externalSource).map(_ -> item)
      )
      .toMap

    val afterIds = getAfterIds()
    val afterItems = getAllAfterItems()
    val afterItemsById =
      afterItems
        .flatMap(item => uniqueKeyForIncoming(item).map(_ -> item))
        .toMap

    val removedIds = beforeIds -- afterIds
    val matchingIds = afterIds.intersect(beforeIds)

    val additions = getAddedItems()

    val removals = removedIds.map(removedId => {
      val esItem = beforeItemsById(removedId)

      PendingAvailabilityRemove(
        esItem,
        createAvailabilityKeys(networks),
        Some(removedId)
      )
    })

    val (changeUpdates, changeRemovals) = matchingIds.toSeq
      .map(id => {
        val before = beforeItemsById(id)
        val after = afterItemsById(id)

        before -> after
      })
      .flatMap {
        case (before, after) =>
          processItemChange(before, after).map(changeType => {
            MatchResult(after, before) -> changeType
          })
      }
      .foldLeft(
        Folds.list2Empty[PendingAvailabilityUpdate, PendingAvailabilityRemove]
      ) {
        case (
            (updates, removes),
            (matchResult, ItemChange(_, _, ItemChangeUpdate))
            ) =>
          val avs = createDeltaAvailabilities(
            networks,
            matchResult.esItem,
            matchResult.scrapedItem,
            isAvailable = true
          )

          (updates :+ PendingAvailabilityUpdate(
            matchResult.esItem,
            Some(matchResult.scrapedItem),
            avs
          )) -> removes

        case (
            (updates, removes),
            (matchResult, ItemChange(_, _, ItemChangeRemove))
            ) =>
          updates -> (removes :+ PendingAvailabilityRemove(
            matchResult.esItem,
            createAvailabilityKeys(networks),
            uniqueKeyForIncoming(matchResult.scrapedItem)
          ))
      }

    val allChanges = additions ++ removals ++ changeUpdates ++ changeRemovals
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

  override protected def uniqueKeyForExisting(item: EsItem): Option[String] = {
    item.externalIdsGrouped.get(externalSource)
  }

  override protected def getBeforeIds(): Set[String] = {
    getAllBeforeItems()
      .flatMap(uniqueKeyForExisting)
      .toSet
  }

  override protected def getAllBeforeItems(): List[EsItem] = {
    getAllBeforeItemsOnce()
  }

  private[this] val getAllBeforeItemsOnce = OnceT {
    val networks = getNetworksOrExit()

    itemsScroller
      .start(
        AvailabilityQueryBuilder.hasAvailabilityForNetworks(
          QueryBuilders.boolQuery(),
          networks
        )
      )
      .filter(item => {
        networks.toList
          .map(_.id)
          .flatMap(item.availabilityGrouped.getOrElse(_, Nil))
          .nonEmpty
      })
      .filter(item => {
        uniqueKeyForExisting(item)
          .exists(id => args.externalIdFilter.forall(_ == id))
      })
      .toList
      .await()
  }

  override protected def getRemovedItems(): List[PendingAvailabilityRemove] = {
    val beforeItems = getAllBeforeItems()
    val removedIds = getRemovedIds()
    val networks = getNetworksOrExit()

    val beforeItemsById = beforeItems
      .flatMap(
        item => uniqueKeyForExisting(item).map(_ -> item)
      )
      .toMap

    removedIds.toList.map(removedId => {
      val esItem = beforeItemsById(removedId)

      PendingAvailabilityRemove(
        esItem,
        createAvailabilityKeys(networks),
        Some(removedId)
      )
    })
  }

  override protected def processItemChange(
    before: EsItem,
    after: T
  ): Seq[ItemChange] = {
    val networks = getNetworksOrExit().toSeq

    val groupedAvailability = before.availabilityGrouped
    networks.flatMap(network => {
      val availabilities = groupedAvailability.getOrElse(network.id, Nil)
      val deltaAvailabilities = createDeltaAvailabilities(
        Set(network),
        before,
        after,
        isAvailable = true
      )

      val existingGrouped =
        availabilities.map(av => EsAvailability.getKey(av) -> av).toMap
      val newGrouped =
        deltaAvailabilities
          .map(av => EsAvailability.getKey(av) -> av)
          .toMap

      existingGrouped.keySet
        .intersect(newGrouped.keySet)
        .flatMap(key => {
          val existing = existingGrouped(key)
          val newAv = newGrouped(key)

          processExistingAvailability(existing, newAv, after, before)
        })
    })
  }

  protected def processExistingAvailability(
    existing: EsAvailability,
    incoming: EsAvailability,
    scrapedItem: T,
    esItem: EsItem
  ): Option[ItemChange]
}
