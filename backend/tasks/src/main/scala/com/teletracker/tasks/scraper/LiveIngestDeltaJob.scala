package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.CrawlerName
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryBuilder,
  ItemsScroller
}
import com.teletracker.common.model.scraping.{
  MatchResult,
  ScrapedItem,
  ScrapedItemAvailabilityDetails
}
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.{Folds, OnceT}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.loaders.{
  CrawlAvailabilityItemLoaderArgs,
  CrawlAvailabilityItemLoaderFactory
}
import io.circe.Codec
import io.circe.generic.JsonCodec
import org.elasticsearch.index.query.QueryBuilders
import java.util.UUID
import scala.concurrent.duration._

@JsonCodec
case class LiveIngestDeltaJobArgs(
  version: Option[Long],
  override val offset: Int,
  override val limit: Int,
  override val itemIdFilter: Option[UUID],
  override val externalIdFilter: Option[String],
  override val deltaSizeThreshold: Double,
  override val disableDeltaSizeCheck: Boolean,
  override val dryRun: Boolean = true,
  override val sleepBetweenWriteMs: Option[Long],
  override val processBatchSleep: Option[FiniteDuration],
  override val parallelism: Option[Int])
    extends IngestDeltaJobArgsLike

abstract class LiveIngestDeltaJob[
  T <: ScrapedItem: ScrapedItemAvailabilityDetails
](
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit codec: Codec[T],
  typedArgs: JsonableArgs[IngestDeltaJobArgs])
    extends IngestDeltaJobLike[EsItem, T, LiveIngestDeltaJobArgs](deps) {
  import ScrapedItemAvailabilityDetails.syntax._

  private val crawlAvailabilityItemLoader =
    crawlAvailabilityItemLoaderFactory.make[T]

  protected def crawlerName: CrawlerName

  override def preparseArgs(args: RawArgs): LiveIngestDeltaJobArgs =
    parseArgs(args)

  private def parseArgs(args: Map[String, Any]): LiveIngestDeltaJobArgs = {
    LiveIngestDeltaJobArgs(
      version = args.value[Long]("version"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      itemIdFilter = args.value[UUID]("itemIdFilter"),
      externalIdFilter = args.value[String]("externalIdFilter"),
      sleepBetweenWriteMs = args.value[Long]("perBatchSleepMs"),
      processBatchSleep = args.value[Long]("perBatchSleepMs").map(_ millis),
      deltaSizeThreshold =
        args.valueOrDefault[Double]("deltaSizeThreshold", 5.0),
      disableDeltaSizeCheck =
        args.valueOrDefault("disableDeltaSizeCheck", false),
      parallelism = args.value[Int]("parallelism")
    )
  }

  override def runInternal(): Unit = {
    val networks = getNetworksOrExit()

    val beforeItems = getAllBeforeItems()

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

  override protected def uniqueKeyForIncoming(item: T): Option[String] = {
    item.uniqueKey
  }

  override protected def externalIds(item: T): Map[ExternalSource, String] = {
    item.externalIds
  }

  override protected def getBeforeIds(): Set[String] = {
    getAllBeforeItems()
      .flatMap(uniqueKeyForExisting)
      .toSet
  }

  override protected def getAllBeforeItems(): List[EsItem] = {
    getAllBeforeItemsOnce()
  }

  override protected def getAfterIds(): Set[String] = {
    getAllAfterItems().flatMap(uniqueKeyForIncoming).toSet
  }

  override protected def getAllAfterItems(): List[T] = {
    crawlAvailabilityItemLoader
      .load(
        CrawlAvailabilityItemLoaderArgs(
          supportedNetworks,
          crawlerName,
          args.version
        )
      )
      .await()
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
