package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.async.EsIngestQueue
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsExternalId,
  EsGenericScrapedItem,
  EsItem,
  EsPotentialMatchItem,
  EsPotentialMatchItemUpdateView,
  EsPotentialMatchState,
  EsScrapedItem,
  UpdateableEsItem
}
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.model.scraping.{
  MatchResult,
  PartialEsItem,
  ScrapedItem
}
import com.teletracker.common.pubsub.EsIngestItemDenormArgs
import com.teletracker.common.tasks.TeletrackerTask.{JsonableArgs, RawArgs}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{AsyncStream, Folds, NetworkCache}
import com.teletracker.tasks.scraper.IngestJobParser.{JsonPerLine, ParseMode}
import com.teletracker.tasks.scraper.matching.{
  CustomElasticsearchLookup,
  ElasticsearchExternalIdLookup,
  ElasticsearchFallbackMatching,
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.util.{FileUtils, SourceRetriever}
import io.circe.generic.JsonCodec
import io.circe.Codec
import io.circe.syntax._
import javax.inject.{Inject, Provider}
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID
import java.util.concurrent.{Executors, ScheduledExecutorService}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.util.control.NonFatal

trait IngestDeltaJobArgsLike {
  val snapshotAfter: URI
  val snapshotBefore: URI
  val offset: Int
  val limit: Int
  val dryRun: Boolean
  val titleMatchThreshold: Int
  val thingIdFilter: Option[UUID]
  val perBatchSleepMs: Option[Int]
  val deltaSizeThreshold: Double
}

@JsonCodec
case class IngestDeltaJobArgs(
  snapshotAfter: URI,
  snapshotBefore: URI,
  offset: Int = 0,
  limit: Int = -1,
  dryRun: Boolean = true,
  titleMatchThreshold: Int = 15,
  thingIdFilter: Option[UUID] = None,
  perBatchSleepMs: Option[Int] = None,
  deltaSizeThreshold: Double = 5.0)
    extends IngestJobArgsLike
    with IngestDeltaJobArgsLike

class IngestDeltaJobDependencies @Inject()(
  val externalIdLookup: ElasticsearchExternalIdLookup.Factory,
  val fileUtils: FileUtils,
  val itemUpdateQueue: EsIngestQueue,
  val esExternalIdMapper: ElasticsearchExternalIdMappingStore,
  val esPotentialMatchItemStore: EsPotentialMatchItemStore,
  val elasticsearchLookup: ElasticsearchLookup,
  val teletrackerConfig: TeletrackerConfig,
  val elasticsearchExecutor: ElasticsearchExecutor,
  val itemLookup: ItemLookup,
  val itemUpdater: ItemUpdater,
  val networkCache: NetworkCache,
  val sourceRetriever: SourceRetriever,
  val executionContext: ExecutionContext,
  @SingleThreaded val singleThreadExecutorProvider: Provider[
    ScheduledExecutorService
  ])

abstract class IngestDeltaJob[T <: ScrapedItem](
  deps: IngestDeltaJobDependencies
)(implicit codec: Codec[T],
  typedArgs: JsonableArgs[IngestDeltaJobArgs])
    extends IngestDeltaJobLike[T, IngestDeltaJobArgs](deps) {
  override def preparseArgs(args: RawArgs): IngestDeltaJobArgs = parseArgs(args)

  private def parseArgs(args: Map[String, Option[Any]]): IngestDeltaJobArgs = {
    IngestDeltaJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.valueOrThrow[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      thingIdFilter = args.value[UUID]("thingIdFilter"),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs"),
      deltaSizeThreshold =
        args.valueOrDefault[Double]("deltaSizeThreshold", 5.0)
    )
  }
}

abstract class IngestDeltaJobLike[
  T <: ScrapedItem,
  IngestJobArgs <: IngestJobArgsLike with IngestDeltaJobArgsLike
](
  deps: IngestDeltaJobDependencies
)(implicit codec: Codec[T],
  typedArgs: JsonableArgs[IngestJobArgs])
    extends BaseIngestJob[T, IngestJobArgs]()(
      typedArgs,
      deps.executionContext,
      codec
    )
    with ElasticsearchFallbackMatching[T, IngestJobArgs] {

  protected val singleThreadExecutor: ScheduledExecutorService =
    deps.singleThreadExecutorProvider.get()

  implicit protected val execCtx: ExecutionContext =
    deps.executionContext

  override protected def teletrackerConfig: TeletrackerConfig =
    deps.teletrackerConfig
  override protected def elasticsearchExecutor: ElasticsearchExecutor =
    deps.elasticsearchExecutor

  protected def supportedNetworks: Set[SupportedNetwork]
  protected def externalSource: ExternalSource

  protected def parseMode: ParseMode = JsonPerLine

  protected def lookupMethod(): LookupMethod[T] =
    new CustomElasticsearchLookup[T](
      List(
        deps.externalIdLookup.create[T](externalSource, uniqueKey),
        deps.elasticsearchLookup.toMethod[T]
      )
    )

  override protected def processMode(): ProcessMode =
    Parallel(16, args.perBatchSleepMs.map(_ millis))

  override def runInternal(): Unit = {
    val networks = getNetworksOrExit()

    val afterIds = deps.fileUtils
      .readAllLinesToUniqueIdSet[T](
        args.snapshotAfter,
        uniqueKey,
        consultSourceCache = true
      )

    logger.info(s"Found ${afterIds.size} IDs in the current snapshot")

    val beforeIds = deps.fileUtils
      .readAllLinesToUniqueIdSet[T](
        args.snapshotBefore,
        uniqueKey,
        consultSourceCache = true
      )

    logger.info(s"Found ${beforeIds.size} IDs in the previous snapshot")

    val newIds = afterIds -- beforeIds
    val removedIds = beforeIds -- afterIds
    val matchingIds = afterIds.intersect(beforeIds)

    val pctChange = Math.abs(
      (beforeIds.size - afterIds.size) / beforeIds.size.toDouble
    ) * 100.0

    if (pctChange >= args.deltaSizeThreshold) {
      handleDiffEarlyExit(newIds = newIds, removedIds = removedIds)

      throw new RuntimeException(
        s"Delta ($pctChange)% exceeded configured threshold of ${args.deltaSizeThreshold}. Before: ${beforeIds.size}, After: ${afterIds.size}"
      )
    }

    logger.info(
      s"Checking for ${newIds.size} new IDs, ${removedIds.size} removed IDs, and ${matchingIds.size} matching IDs."
    )

    val afterItemSource =
      deps.sourceRetriever.getSource(args.snapshotAfter, consultCache = true)

    val (afterItems, afterItemsById) = readItems(afterItemSource)

    val (addedMatches, addedNotFound) = AsyncStream
      .fromSeq(afterItems)
      .filter(item => newIds.contains(uniqueKey(item)))
      .throughApply(processAll(_, networks))
      .map {
        case (matchResults, nonMatches) =>
          val filteredResults = matchResults.filter {
            case MatchResult(_, esItem) =>
              args.thingIdFilter.forall(_ == esItem.id)
          }

          filteredResults -> nonMatches
      }
      .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
      .await()

    val newAvailabilities = addedMatches
      .filter {
        case MatchResult(_, esItem) =>
          args.thingIdFilter.forall(_ == esItem.id)
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          val newAvailabilities = createDeltaAvailabilities(
            networks,
            esItem,
            scrapedItem,
            isAvailable = true
          )
          PendingAvailabilityUpdates(esItem, scrapedItem, newAvailabilities)
      }

    val beforeItemSource =
      deps.sourceRetriever.getSource(args.snapshotBefore, consultCache = true)

    val (beforeItems, beforeItemsById) = readItems(beforeItemSource)

    val (beforeMatches, beforeNotFound) = AsyncStream
      .fromSeq(beforeItems)
      .filter(item => removedIds.contains(uniqueKey(item)))
      .throughApply(processAll(_, networks))
      .map {
        case (matchResults, nonMatches) =>
          val filteredResults = matchResults.filter {
            case MatchResult(_, esItem) =>
              args.thingIdFilter.forall(_ == esItem.id)
          }

          filteredResults -> nonMatches
      }
      .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
      .await()

    val removedAvailabilities = beforeMatches
      .filter {
        case MatchResult(_, esItem) =>
          args.thingIdFilter.forall(_ == esItem.id)
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          val newAvailabilities = createDeltaAvailabilities(
            networks,
            esItem,
            scrapedItem,
            isAvailable = false
          )

          PendingAvailabilityUpdates(esItem, scrapedItem, newAvailabilities)
      }

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
        uniqueKey(change.after) -> change
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
      .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
      .await()

    val (updateChanges, removeChanges) = changedMatches
      .filter {
        case MatchResult(_, esItem) =>
          args.thingIdFilter.forall(_ == esItem.id)
      }
      .foldLeft(
        Folds.list2Empty[PendingAvailabilityUpdates, PendingAvailabilityUpdates]
      ) {
        case ((adds, removes), MatchResult(scrapedItem, esItem)) =>
          itemChangesById.get(uniqueKey(scrapedItem)) match {
            case Some(ItemChange(_, _, changeType)) =>
              val isAvailable = changeType match {
                case ItemChangeUpdate => true
                case ItemChangeRemove => false
              }

              val availabilities = createDeltaAvailabilities(
                networks,
                esItem,
                scrapedItem,
                isAvailable = isAvailable
              )

              val pendingUpdates =
                PendingAvailabilityUpdates(esItem, scrapedItem, availabilities)
              if (isAvailable) {
                (adds :+ pendingUpdates) -> removes
              } else {
                adds -> (removes :+ pendingUpdates)
              }
            case None => adds -> removes
          }
      }

    if (addedNotFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for added items: $addedNotFound"
      )
    }

    if (beforeNotFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for removed items: $beforeNotFound"
      )
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
      s"Found ${(removedAvailabilities ++ removeChanges).flatMap(_.availabilities).size} availabilities to remove"
    )

    if (!args.dryRun) {
      val allAdds = newAvailabilities ++ updateChanges
      val allRemoves = removedAvailabilities ++ removeChanges
      logger.info(
        s"Saving ${allAdds.size + allRemoves.size} availabilities"
      )

      saveExternalIdMappings(allAdds, allRemoves)
        .flatMap(_ => {
          saveAvailabilities(
            allAdds,
            allRemoves,
            shouldRetry = true
          )
        })
        .await()
    } else {
      val today = LocalDate.now()
      val changes = new File(
        s"${today}_${getClass.getSimpleName}-changes.json"
      )
      val changesPrinter = new PrintWriter(
        new BufferedOutputStream(new FileOutputStream(changes))
      )

      val header = List(
        "change_type",
        "es_item_id",
        "external_id",
        "title"
      ).mkString(",")

      changesPrinter.println(header)

      (newAvailabilities ++ updateChanges).foreach(av => {
        logger.info(
          s"Would've added availability (ID: ${av.esItem.id}, external: ${uniqueKey(
            av.item
          )}, name: ${av.esItem.title.get.head}): ${av.availabilities}"
        )

        changesPrinter.println(
          List(
            "add",
            av.esItem.id,
            uniqueKey(av.item),
            "\"" + av.esItem.title.get.head + "\""
          ).mkString(",")
        )
      })

      (removedAvailabilities ++ removeChanges).foreach(av => {
        logger.info(
          s"Would've removed availability (ID: ${av.esItem.id}, external: ${uniqueKey(
            av.item
          )}, name: ${av.esItem.title.get.head}): ${av.availabilities}"
        )
        changesPrinter.println(
          List(
            "remove",
            av.esItem.id,
            uniqueKey(av.item),
            "\"" + av.esItem.title.get.head + "\""
          ).mkString(",")
        )
      })

      changesPrinter.flush()
      changesPrinter.close()
    }
  }

  protected def handleDiffEarlyExit(
    newIds: Set[String],
    removedIds: Set[String]
  ): Unit = {
    val beforeItemSource =
      deps.sourceRetriever.getSource(args.snapshotBefore, consultCache = true)
    val (beforeItems, _) = readItems(beforeItemSource)

    val afterItemSource =
      deps.sourceRetriever.getSource(args.snapshotAfter, consultCache = true)
    val (afterItems, _) = readItems(afterItemSource)

    val addedItems = afterItems
      .filter(item => newIds.contains(uniqueKey(item)))

    val removedItems = beforeItems
      .filter(item => removedIds.contains(uniqueKey(item)))

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

  private def filterMatchResults(
    matches: List[MatchResult[T]],
    nonMatches: List[T]
  ) = {
    val filteredResults = matches.filter {
      case MatchResult(_, esItem) =>
        args.thingIdFilter.forall(_ == esItem.id)
    }

    filteredResults -> nonMatches
  }

  private def readItems(source: Source) = {
    val items = new IngestJobParser()
      .asyncStream[T](source.getLines())
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
    val itemsById = items.map(item => uniqueKey(item) -> item).toMap

    (items, itemsById)
  }

  protected def saveExternalIdMappings(
    newAvailabilities: List[PendingAvailabilityUpdates],
    availabilitiesToRemove: List[PendingAvailabilityUpdates]
  ): Future[Unit] = {
    val allExternalIdUpdates = (newAvailabilities ++ availabilitiesToRemove)
      .map(update => {
        externalIds(update.item).map {
          case (source, str) =>
            (EsExternalId(source, str), update.esItem.`type`) -> update.esItem.id
        }
      })
      .foldLeft(Map.empty[(EsExternalId, ItemType), UUID])(_ ++ _)

    if (args.dryRun) {
      Future.successful(
        logger.info(
          s"Would've updated ${allExternalIdUpdates.size} external id mappings"
        )
      )
    } else {
      deps.esExternalIdMapper.mapExternalIds(allExternalIdUpdates)
    }
  }

  protected def saveAvailabilities(
    newAvailabilities: List[PendingAvailabilityUpdates],
    availabilitiesToRemove: List[PendingAvailabilityUpdates],
    shouldRetry: Boolean
  ): Future[Unit] = {
    val externalIdsById = (newAvailabilities ++ availabilitiesToRemove)
      .map(update => {
        update.esItem.id -> uniqueKey(update.item)
      })
      .toMap

    val newAvailabilityByThingId =
      newAvailabilities
        .groupBy(_.esItem.id)
        .mapValues(_.flatMap(_.availabilities))

    val removalAvailabilityByThingId =
      availabilitiesToRemove
        .groupBy(_.esItem.id)
        .mapValues(_.flatMap(_.availabilities))

    val allItemIds = newAvailabilityByThingId.keySet ++ removalAvailabilityByThingId.keySet

    AsyncStream
      .fromSeq(allItemIds.toSeq)
      .grouped(16)
      .delayedMapF(
        args.perBatchSleepMs.map(_ millis).getOrElse(250 millis),
        singleThreadExecutor
      )(batch => {
        val updates = batch.toList.map(itemId => {
          val newAvailabilities = newAvailabilityByThingId
            .getOrElse(itemId, Seq.empty)
            .groupBy(EsAvailability.distinctFields)
            .values
            .flatMap(_.headOption)

          val removalAvailabilities = removalAvailabilityByThingId
            .getOrElse(itemId, Seq.empty)
            .groupBy(EsAvailability.distinctFields)
            .values
            .flatMap(_.headOption)

          deps.itemLookup
            .lookupItem(
              Left(itemId),
              None,
              shouldMateralizeCredits = false,
              shouldMaterializeRecommendations = false
            )
            .flatMap {
              case None => Future.successful(None)
              case Some(item) =>
                val availabilitiesToSave = item.rawItem.availability match {
                  case Some(value) =>
                    val withoutRemovalsAndDupes =
                      value.filterNot(availability => {
                        removalAvailabilities.exists(
                          EsAvailability.availabilityEquivalent(_, availability)
                        ) || newAvailabilities.exists(
                          EsAvailability.availabilityEquivalent(_, availability)
                        )
                      })

                    withoutRemovalsAndDupes ++ newAvailabilities

                  case None =>
                    newAvailabilities
                }

                val existingExternalIds = item.rawItem.externalIdsGrouped
                val newExternalIds = externalIdsById
                  .get(itemId)
                  .map(newExternalId => {
                    existingExternalIds.updated(externalSource, newExternalId)
                  })
                  .getOrElse(existingExternalIds)
                  .map {
                    case (source, id) => EsExternalId(source, id)
                  }

                deps.itemUpdateQueue.queueItemUpdate(
                  id = item.rawItem.id,
                  itemType = item.rawItem.`type`,
                  doc = item.rawItem
                    .copy(
                      availability = Some(availabilitiesToSave.toList),
                      external_ids = Some(newExternalIds.toList)
                    )
                    .asJson,
                  denorm = Some(
                    EsIngestItemDenormArgs(
                      needsDenorm = true,
                      cast = false,
                      crew = false
                    )
                  )
                )
            }
        })

        Future
          .sequence(updates)
          .recover {
            case NonFatal(e) =>
              logger.error(s"Error while handling batch. ${if (shouldRetry) "Scheduling for retry"
              else ""}", e)
              if (shouldRetry) batch.toSet else Set()
          }
          .map(_ => Set.empty[UUID])
      })
      .foldLeft(Set.empty[UUID])(_ ++ _)
      .flatMap {
        case Seq() => Future.unit
        case failures if shouldRetry =>
          saveAvailabilities(
            newAvailabilities
              .filter(update => failures.contains(update.esItem.id)),
            availabilitiesToRemove
              .filter(update => failures.contains(update.esItem.id)),
            shouldRetry = false
          )
        case failures =>
          logger.warn(s"${failures.size} permanently failed")
          Future.unit
      }
  }

  override protected def writePotentialMatches(
    potentialMatches: Iterable[(EsItem, T)]
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

  protected def getNetworksOrExit(): Set[StoredNetwork] = {
    val foundNetworks = deps.networkCache
      .getAllNetworks()
      .await()
      .collect {
        case network
            if network.supportedNetwork.isDefined && supportedNetworks.contains(
              network.supportedNetwork.get
            ) =>
          network
      }
      .toSet

    if (supportedNetworks
          .diff(foundNetworks.flatMap(_.supportedNetwork))
          .nonEmpty) {
      throw new IllegalStateException(
        s"""Could not find all networks "${supportedNetworks}" network from datastore"""
      )
    }

    foundNetworks
  }

  protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: T,
    isAvailable: Boolean
  ): List[EsAvailability]

  protected def uniqueKey(item: T): String

  protected def externalIds(item: T): Map[ExternalSource, String]

  override protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: ArgsType
  ): Future[Unit] = Future.unit

  // Override if items appearing on both sides of the diff could have produced some change.
  protected def processItemChange(
    before: T,
    after: T
  ): Option[ItemChange] = {
    None
  }

  case class PendingAvailabilityUpdates(
    esItem: EsItem,
    item: T,
    availabilities: List[EsAvailability])

  case class ItemChange(
    before: T,
    after: T,
    changeType: ItemChangeType)

  sealed trait ItemChangeType
  case object ItemChangeUpdate extends ItemChangeType
  case object ItemChangeRemove extends ItemChangeType
}
