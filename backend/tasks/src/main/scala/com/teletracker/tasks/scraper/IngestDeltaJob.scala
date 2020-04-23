package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  EsExternalId,
  EsItem,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.Functions._
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{Folds, NetworkCache}
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.scraper.matching.{
  CustomElasticsearchLookup,
  ElasticsearchExternalIdLookup,
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Codec, Encoder}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
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
}

case class IngestDeltaJobArgs(
  snapshotAfter: URI,
  snapshotBefore: URI,
  offset: Int = 0,
  limit: Int = -1,
  dryRun: Boolean = true,
  titleMatchThreshold: Int = 15,
  thingIdFilter: Option[UUID] = None,
  perBatchSleepMs: Option[Int] = None)
    extends IngestJobArgsLike
    with IngestDeltaJobArgsLike

abstract class IngestDeltaJob[T <: ScrapedItem](
  elasticsearchLookup: ElasticsearchLookup
)(implicit codec: Codec[T])
    extends IngestDeltaJobLike[T, IngestDeltaJobArgs](elasticsearchLookup) {
  implicit override protected def typedArgsEncoder
    : Encoder[IngestDeltaJobArgs] = deriveEncoder[IngestDeltaJobArgs]

  override def preparseArgs(args: Args): IngestDeltaJobArgs = parseArgs(args)

  private def parseArgs(args: Map[String, Option[Any]]): IngestDeltaJobArgs = {
    IngestDeltaJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.valueOrThrow[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      thingIdFilter = args.value[UUID]("thingIdFilter"),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs")
    )
  }
}

abstract class IngestDeltaJobLike[
  T <: ScrapedItem,
  ArgsType <: IngestJobArgsLike with IngestDeltaJobArgsLike
](
  elasticsearchLookup: ElasticsearchLookup
)(implicit codec: Codec[T])
    extends BaseIngestJob[T, ArgsType]()(
      scala.concurrent.ExecutionContext.Implicits.global,
      codec
    ) {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  @Inject
  private[this] var externalIdLookup: ElasticsearchExternalIdLookup.Factory = _

  protected def s3: S3Client
  protected def itemLookup: ItemLookup
  protected def itemUpdater: ItemUpdater

  protected def networkNames: Set[String]
  protected def networkCache: NetworkCache
  protected def externalSource: ExternalSource

  protected def parseMode: ParseMode = AllJson
  protected def lookupMethod: LookupMethod[T] =
    new CustomElasticsearchLookup[T](
      List(
        externalIdLookup.create[T](externalSource, uniqueKey),
        elasticsearchLookup.toMethod[T]
      )
    )

  override protected def processMode(args: ArgsType): ProcessMode =
    Parallel(16, args.perBatchSleepMs.map(_ millis))

  override type TypedArgs = ArgsType

  override def runInternal(args: Args): Unit = {
    val networks = getNetworksOrExit()

    val parsedArgs = preparseArgs(args)
    val sourceRetriever = new SourceRetriever(s3)

    val afterSource =
      sourceRetriever.getSource(parsedArgs.snapshotAfter)
    val beforeSource =
      sourceRetriever.getSource(parsedArgs.snapshotBefore)

    val parser = new IngestJobParser()

    val afterIds = try {
      parser
        .stream[T](afterSource.getLines())
        .flatMap {
          case Left(NonFatal(ex)) =>
            logger.warn(s"Error parsing line: ${ex.getMessage}")
            None
          case Right(value) => Some(uniqueKey(value))
        }
        .toSet
    } finally {
      afterSource.close()
    }

    logger.info(s"Found ${afterIds.size} IDs in the current snapshot")

    val beforeIds = try {
      parser
        .stream[T](beforeSource.getLines())
        .flatMap {
          case Left(NonFatal(ex)) =>
            logger.warn(s"Error parsing line: ${ex.getMessage}")
            None
          case Right(value) => Some(uniqueKey(value))
        }
        .toSet
    } finally {
      beforeSource.close()
    }

    logger.info(s"Found ${beforeIds.size} IDs in the previous snapshot")

    val newIds = afterIds -- beforeIds
    val removedIds = beforeIds -- afterIds

    logger.info(
      s"Checking for ${newIds.size} new IDs and ${removedIds.size} removed IDs"
    )

    val afterItemSource =
      sourceRetriever.getSource(parsedArgs.snapshotAfter, consultCache = true)

    val (addedMatches, addedNotFound) = try {
      parser
        .asyncStream[T](afterItemSource.getLines())
        .flatMapOption {
          case Left(NonFatal(ex)) =>
            logger.warn(s"Error parsing line: ${ex.getMessage}")
            None

          case Right(value) if newIds.contains(uniqueKey(value)) =>
            Some(value)

          case _ => None
        }
        .throughApply(processAll(_, networks, parsedArgs))
        .map {
          case (matchResults, nonMatches) =>
            val filteredResults = matchResults.filter {
              case MatchResult(_, esItem) =>
                parsedArgs.thingIdFilter.forall(_ == esItem.id)
            }

            filteredResults -> nonMatches
        }
        .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
        .await()
    } finally {
      afterItemSource.close()
    }

    val newAvailabilities = addedMatches
      .filter {
        case MatchResult(_, esItem) =>
          parsedArgs.thingIdFilter.forall(_ == esItem.id)
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          val newAvailabilities = createAvailabilities(
            networks,
            esItem.id,
            esItem.title.get.headOption.orElse(esItem.original_title).get,
            scrapedItem,
            isAvailable = true
          )
          PendingAvailabilityUpdates(esItem, scrapedItem, newAvailabilities)
      }

    logger.info(
      s"Found ${newAvailabilities.flatMap(_.availabilities).size} availabilities to add"
    )

    if (addedNotFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for added items: ${addedNotFound}"
      )
    }

    val beforeItemSource =
      sourceRetriever.getSource(parsedArgs.snapshotBefore, consultCache = true)

    val (beforeMatches, beforeNotFound) = try {
      parser
        .asyncStream[T](beforeItemSource.getLines())
        .flatMapOption {
          case Left(NonFatal(ex)) =>
            logger.warn(s"Error parsing line: ${ex.getMessage}")
            None

          case Right(value) if removedIds.contains(uniqueKey(value)) =>
            Some(value)

          case _ => None
        }
        .throughApply(processAll(_, networks, parsedArgs))
        .map {
          case (matchResults, nonMatches) =>
            val filteredResults = matchResults.filter {
              case MatchResult(_, esItem) =>
                parsedArgs.thingIdFilter.forall(_ == esItem.id)
            }

            filteredResults -> nonMatches
        }
        .foldLeft(Folds.list2Empty[MatchResult[T], T])(Folds.fold2Append)
        .await()
    } finally {
      beforeItemSource.close()
    }

    val removedAvailabilities = beforeMatches
      .filter {
        case MatchResult(_, esItem) =>
          parsedArgs.thingIdFilter.forall(_ == esItem.id)
      }
      .map {
        case MatchResult(scrapedItem, esItem) =>
          val newAvailabilities = createAvailabilities(
            networks,
            esItem.id,
            esItem.title.get.headOption.orElse(esItem.original_title).get,
            scrapedItem,
            isAvailable = false
          )

          PendingAvailabilityUpdates(esItem, scrapedItem, newAvailabilities)
      }

    if (beforeNotFound.nonEmpty) {
      logger.warn(
        s"Could not find matches for added items: ${beforeNotFound}"
      )
    }

    logger.info(
      s"Found ${removedAvailabilities.flatMap(_.availabilities).size} availabilities to remove"
    )

    if (!parsedArgs.dryRun) {
      logger.info(
        s"Saving ${(newAvailabilities ++ removedAvailabilities).size} availabilities"
      )
      saveAvailabilities(newAvailabilities, removedAvailabilities).await()
    } else {
      newAvailabilities.foreach(av => {
        logger.info(
          s"Would've added availability (ID: ${av.esItem.id}, external: ${uniqueKey(
            av.item
          )}, name: ${av.esItem.title.get.head}): ${av.availabilities}"
        )
      })

      removedAvailabilities.foreach(av => {
        logger.info(
          s"Would've removed availability (ID: ${av.esItem.id}, external: ${uniqueKey(
            av.item
          )}, name: ${av.esItem.title.get.head}): ${av.availabilities}"
        )
      })
    }
  }

  protected def saveAvailabilities(
    newAvailabilities: List[PendingAvailabilityUpdates],
    availabilitiesToRemove: List[PendingAvailabilityUpdates]
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

    val updates = allItemIds.toList.map(itemId => {
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

      itemLookup
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
                val withoutRemovalsAndDupes = value.filterNot(availability => {
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

            itemUpdater
              .update(
                item.rawItem
                  .copy(
                    availability = Some(availabilitiesToSave.toList),
                    external_ids = Some(newExternalIds.toList)
                  )
              )
              .map(Some(_))
        }
    })

    Future.sequence(updates).map(_ => {})
  }

  protected def getNetworksOrExit(): Set[StoredNetwork] = {
    val foundNetworks = networkCache
      .getAllNetworks()
      .await()
      .collect {
        case network if networkNames.contains(network.slug.value) =>
          network
      }
      .toSet

    if (networkNames.diff(foundNetworks.map(_.slug.value)).nonEmpty) {
      throw new IllegalStateException(
        s"""Could not find all networks "${networkNames}" network from datastore"""
      )
    }

    foundNetworks
  }

  protected def createAvailabilities(
    networks: Set[StoredNetwork],
    itemId: UUID,
    title: String,
    scrapedItem: T,
    isAvailable: Boolean
  ): List[EsAvailability]

  protected def uniqueKey(item: T): String

  override protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: ArgsType
  ): Future[Unit] = Future.unit

  case class PendingAvailabilityUpdates(
    esItem: EsItem,
    item: T,
    availabilities: List[EsAvailability])
}
