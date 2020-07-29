package com.teletracker.tasks.scraper

import com.teletracker.common.availability.CrawlerInfo
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
import com.teletracker.common.tasks.args.ArgParser.Millis
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.{Folds, OnceT}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.loaders.{
  CrawlAvailabilityItemLoaderArgs,
  CrawlAvailabilityItemLoaderFactory
}
import io.circe.Codec
import io.circe.generic.JsonCodec
import io.circe.syntax._
import org.elasticsearch.index.query.QueryBuilders
import shapeless.tag.@@
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@JsonCodec
@GenArgParser
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
  override val processBatchSleep: Option[FiniteDuration @@ Millis],
  override val parallelism: Option[Int],
  additionsOnly: Boolean = false)
    extends IngestDeltaJobArgsLike

object LiveIngestDeltaJobArgs

abstract class LiveIngestDeltaJob[
  T <: ScrapedItem: ScrapedItemAvailabilityDetails
](
  deps: IngestDeltaJobDependencies,
  itemsScroller: ItemsScroller,
  crawlAvailabilityItemLoaderFactory: CrawlAvailabilityItemLoaderFactory
)(implicit codec: Codec[T],
  typedArgs: JsonableArgs[IngestDeltaJobArgs],
  exeuctionContext: ExecutionContext)
    extends IngestDeltaJobLike[EsItem, T, LiveIngestDeltaJobArgs](deps) {
  import ScrapedItemAvailabilityDetails.syntax._

  private val crawlAvailabilityItemLoader =
    crawlAvailabilityItemLoaderFactory.make[T]

  protected def crawlerName: CrawlerName

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

    val addedIds = afterIds -- beforeIds
    val removedIds = beforeIds -- afterIds
    val matchingIds = afterIds.intersect(beforeIds)

    if (!args.additionsOnly) {
      val pctChange = Math.abs(
        (beforeIds.size - afterIds.size) / beforeIds.size.toDouble
      ) * 100.0

      if (pctChange >= args.deltaSizeThreshold && !args.disableDeltaSizeCheck) {
        handleDiffEarlyExit(newIds = addedIds, removedIds = removedIds)

        throw new RuntimeException(
          s"Delta $pctChange% exceeded configured threshold of ${args.deltaSizeThreshold}. Before: ${beforeIds.size}, After: ${afterIds.size}"
        )
      }

    }

    logger.info(
      s"Checking for ${addedIds.size} added IDs, ${removedIds.size} removed IDs, and ${matchingIds.size} matching IDs."
    )

    val additions = getAddedItems()

    val removals = if (args.additionsOnly) {
      Nil
    } else {
      removedIds.toList
        .map(removedId => {
          val esItem = beforeItemsById(removedId)

          PendingAvailabilityRemove(
            esItem,
            createAvailabilityKeys(networks),
            Some(removedId)
          )
        })
        .through(removals => {
          processRemovals(removals).await()
        })
    }

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

    writeChangesFile(allChanges)

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
          crawlAvailabilityItemLoader.getLoadedVersion.orElse(args.version)
        )
      )
      .await()
      .filter(shouldIncludeAfterItem)
      .safeTake(args.limit)
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

  protected def processRemovals(
    removals: List[PendingAvailabilityRemove]
  ): Future[List[PendingAvailabilityRemove]] = Future.successful(removals)

  override protected def getContext: Option[IngestJobContext] = {
    Some(
      IngestJobContext(
        Some(
          CrawlerInfo(
            crawler = crawlerName,
            version = crawlAvailabilityItemLoader.getLoadedVersion
          )
        )
      )
    )
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
      .filter(item => containsExistingUniqueKey(removedIds, item))

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
}
