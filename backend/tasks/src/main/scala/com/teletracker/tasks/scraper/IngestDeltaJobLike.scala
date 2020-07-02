package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  ItemType,
  OfferType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.async.EsIngestQueue
import com.teletracker.common.elasticsearch.lookups.ElasticsearchExternalIdMappingStore
import com.teletracker.common.elasticsearch.model.EsAvailability.AvailabilityKey
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsExternalId,
  EsItem
}
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.elasticsearch.util.ItemUpdateApplier
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.pubsub.EsIngestItemDenormArgs
import com.teletracker.common.tasks.TeletrackerTask.JsonableArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.{AsyncStream, Folds, NetworkCache}
import com.teletracker.tasks.scraper.IngestJobParser.{JsonPerLine, ParseMode}
import com.teletracker.tasks.scraper.matching._
import com.teletracker.tasks.util.{FileUtils, SourceRetriever}
import io.circe.Codec
import io.circe.syntax._
import io.circe.generic.JsonCodec
import javax.inject.{Inject, Provider}
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait IngestDeltaJobArgsLike extends IngestJobArgsLike {
  val itemIdFilter: Option[UUID]
  val externalIdFilter: Option[String]
  val deltaSizeThreshold: Double
  val disableDeltaSizeCheck: Boolean
}

@JsonCodec
case class IngestDeltaJobArgs(
  snapshotAfter: URI,
  snapshotBefore: Option[URI],
  override val offset: Int = 0,
  override val limit: Int = -1,
  dryRun: Boolean = true,
  itemIdFilter: Option[UUID] = None,
  externalIdFilter: Option[String] = None,
  override val parallelism: Option[Int] = None,
  override val processBatchSleep: Option[FiniteDuration] = None,
  deltaSizeThreshold: Double = 5.0,
  disableDeltaSizeCheck: Boolean = false,
  override val sleepBetweenWriteMs: Option[Long] = None)
    extends IngestDeltaJobArgsLike

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

abstract class IngestDeltaJobLike[
  ExistingItemType,
  IncomingItemType <: ScrapedItem,
  IngestJobArgs <: IngestDeltaJobArgsLike
](
  protected val deps: IngestDeltaJobDependencies
)(implicit codec: Codec[IncomingItemType],
  typedArgs: JsonableArgs[IngestJobArgs])
    extends BaseIngestJob[IncomingItemType, IngestJobArgs]()(
      typedArgs,
      deps.executionContext,
      codec
    ) {

  protected val singleThreadExecutor: ScheduledExecutorService =
    deps.singleThreadExecutorProvider.get()

  implicit protected val execCtx: ExecutionContext =
    deps.executionContext

  protected def supportedNetworks: Set[SupportedNetwork]
  protected def externalSource: ExternalSource
  protected def offerType: OfferType

  protected def parseMode: ParseMode = JsonPerLine

  protected def lookupMethod(): LookupMethod[IncomingItemType] =
    new CustomElasticsearchLookup[IncomingItemType](
      List(
        deps.externalIdLookup
          .createOpt[IncomingItemType](externalSource, uniqueKeyForIncoming),
        deps.elasticsearchLookup.toMethod[IncomingItemType]
      )
    )

  protected def getAfterIds(): Set[String]

  protected def getBeforeIds(): Set[String]

  protected def getAllAfterItems(): List[IncomingItemType]

  protected def getAllBeforeItems(): List[ExistingItemType]

  protected def getRemovedIds(): Set[String] = {
    getBeforeIds() -- getAfterIds()
  }

  protected def getAddedIds(): Set[String] = {
    getAfterIds() -- getBeforeIds()
  }

  protected def getRemovedItems(): List[PendingAvailabilityRemove]

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
      .foldLeft(
        Folds.list2Empty[MatchResult[IncomingItemType], IncomingItemType]
      )(
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

  protected def saveAvailabilities(
    pendingUpdates: List[PendingChange],
    shouldRetry: Boolean
  ): Future[Unit] = {
    val externalIdsById =
      pendingUpdates
        .flatMap(update => {
          update.externalId.map(update.esItem.id -> _)
        })
        .toMap

    val availabilityUpdatesByItemId =
      pendingUpdates
        .collect { case x: PendingUpdate => x }
        .groupBy(_.esItem.id)
        .mapValues(_.flatMap(_.availabilities))

    val availabilityRemovalsByItemId =
      pendingUpdates
        .collect {
          case x: PendingAvailabilityRemove => x
        }
        .groupBy(_.esItem.id)
        .mapValues(_.flatMap(_.removes).toSet)

    val allItemIds = availabilityUpdatesByItemId.keySet ++ availabilityRemovalsByItemId.keySet

    AsyncStream
      .fromSeq(allItemIds.toSeq)
      .grouped(16)
      .delayedMapF(
        args.sleepBetweenWriteMs.map(_ millis).getOrElse(250 millis),
        singleThreadExecutor
      )(batch => {
        val updates = batch.toList.map(itemId => {
          val updatedAvailabilities = availabilityUpdatesByItemId
            .getOrElse(itemId, Seq.empty)
            .groupBy(EsAvailability.getKey)
            .values
            .flatMap(_.headOption)

          val removedAvailabilities =
            availabilityRemovalsByItemId
              .getOrElse(itemId, Set.empty)

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
                val newExternalIds = EsExternalId.fromMap(
                  externalIdsById
                    .get(itemId)
                    .map(externalSource -> _)
                    .map(item.rawItem.externalIdsGrouped + _)
                    .getOrElse(item.rawItem.externalIdsGrouped)
                )

                val newItem = item.rawItem.copy(
                  availability = Some(
                    ItemUpdateApplier
                      .applyAvailabilityDelta(
                        item.rawItem,
                        updatedAvailabilities,
                        removedAvailabilities
                      )
                      .toList
                  ),
                  external_ids = newExternalIds
                )

                deps.itemUpdateQueue.queueItemUpdate(
                  id = item.rawItem.id,
                  itemType = item.rawItem.`type`,
                  doc = newItem.asJson,
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
            pendingUpdates
              .filter(update => failures.contains(update.esItem.id)),
            shouldRetry = false
          )
        case failures =>
          logger.warn(s"${failures.size} permanently failed")
          Future.unit
      }
  }

  protected def writeChangesFile(allChanges: List[PendingChange]): Unit = {
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

    allChanges.foreach {
      case update: PendingUpdate =>
        val typ = update match {
          case PendingAvailabilityAdd(_, _, _)    => "added"
          case PendingAvailabilityUpdate(_, _, _) => "updated"
        }

        logger.debug(
          s"Would've ${typ} availability (ID: ${update.esItem.id}, external: ${update.scrapedItem
            .flatMap(uniqueKeyForIncoming)}, name: ${update.esItem.title.get.head}): ${update.availabilities}"
        )

        changesPrinter.println(
          List(
            typ,
            update.esItem.id,
            update.scrapedItem.flatMap(uniqueKeyForIncoming).getOrElse("\"\""),
            "\"" + update.esItem.title.get.head + "\""
          ).mkString(",")
        )

      case PendingAvailabilityRemove(esItem, removes, externalId) =>
        logger.debug(
          s"""Would've removed availability (ID: ${esItem.id}, external: ${externalId},
            name: ${esItem.title.get.head}): ${removes}"""
        )

        changesPrinter.println(
          List(
            "remove",
            esItem.id,
            externalId.getOrElse("\"\""),
            "\"" + esItem.title.get.head + "\""
          ).mkString(",")
        )
    }

    changesPrinter.flush()
    changesPrinter.close()
  }

  protected def saveExternalIdMappings(
    pendingChanges: List[PendingChange]
  ): Future[Unit] = {
    val allExternalIdUpdates = pendingChanges
      .map(update => {
        update.scrapedItem.map(externalIds).getOrElse(Nil).map {
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

  protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: IncomingItemType,
    isAvailable: Boolean
  ): List[EsAvailability]

  protected def createAvailabilityKeys(
    networks: Set[StoredNetwork]
  ): Set[AvailabilityKey] = {
    for {
      network <- networks
      presentationType <- presentationTypes
    } yield {
      AvailabilityKey(
        network.id,
        "US",
        offerType.toString,
        Some(presentationType.toString)
      )
    }
  }

  protected def uniqueKeyForIncoming(item: IncomingItemType): Option[String]

  protected def uniqueKeyForExisting(item: ExistingItemType): Option[String]

  protected def containsUniqueKey(
    it: Set[String],
    item: IncomingItemType
  ): Boolean = uniqueKeyForIncoming(item).exists(it.contains)

  protected def externalIds(item: IncomingItemType): Map[ExternalSource, String]

  override protected def handleMatchResults(
    results: List[MatchResult[IncomingItemType]],
    networks: Set[StoredNetwork],
    args: ArgsType
  ): Future[Unit] = Future.unit

  // Override if items appearing on both sides of the diff could have produced some change.
  protected def processItemChange(
    before: ExistingItemType,
    after: IncomingItemType
  ): Seq[ItemChange] = {
    Seq.empty
  }

  sealed trait PendingChange {
    def esItem: EsItem
    def scrapedItem: Option[IncomingItemType]
    def externalId
      : Option[String] // The unique network-specific item associated with the update
  }

  sealed trait PendingUpdate extends PendingChange {
    def availabilities: List[EsAvailability]
  }

  case class PendingAvailabilityAdd(
    esItem: EsItem,
    scrapedItem: Option[IncomingItemType],
    availabilities: List[EsAvailability])
      extends PendingUpdate {
    override val externalId: Option[String] =
      scrapedItem.flatMap(uniqueKeyForIncoming)
  }

  case class PendingAvailabilityRemove(
    esItem: EsItem,
    removes: Set[AvailabilityKey],
    externalId: Option[String])
      extends PendingChange {
    override def scrapedItem: Option[IncomingItemType] = None
  }

  case class PendingAvailabilityUpdate(
    esItem: EsItem,
    scrapedItem: Option[IncomingItemType],
    availabilities: List[EsAvailability])
      extends PendingUpdate {
    override val externalId: Option[String] =
      scrapedItem.flatMap(uniqueKeyForIncoming)
  }

  case class ItemChange(
    before: ExistingItemType,
    after: IncomingItemType,
    changeType: ItemChangeType)

  sealed trait ItemChangeType
  case object ItemChangeUpdate extends ItemChangeType
  case object ItemChangeRemove extends ItemChangeType
}
