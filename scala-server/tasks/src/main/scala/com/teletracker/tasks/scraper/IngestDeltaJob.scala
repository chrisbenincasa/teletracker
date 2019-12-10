package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{Availability, Network, OfferType}
import com.teletracker.common.elasticsearch
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.{Codec, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import java.net.URI
import java.time.ZoneOffset
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.{Folds, NetworkCache}
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import com.teletracker.common.util.Functions._
import com.teletracker.tasks.scraper.model.MatchResult
import scala.util.control.NonFatal

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

abstract class IngestDeltaJob[T <: ScrapedItem](
  elasticsearchLookup: ElasticsearchLookup
)(implicit codec: Codec[T])
    extends BaseIngestJob[T, IngestDeltaJobArgs]()(
      scala.concurrent.ExecutionContext.Implicits.global,
      codec
    ) {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected def s3: S3Client

  protected def networkNames: Set[String]
  protected def networkCache: NetworkCache

  protected def parseMode: ParseMode = AllJson
  protected def matchMode: MatchMode = elasticsearchLookup

  override protected def processMode(args: IngestDeltaJobArgs): ProcessMode =
    Parallel(4, args.perBatchSleepMs.map(_ millis))

  override type TypedArgs = IngestDeltaJobArgs

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

  override def runInternal(args: Args): Unit = {
    val networks = getNetworksOrExit()

    val parsedArgs = parseArgs(args)
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

    val newIds = afterIds -- beforeIds
    val removedIds = beforeIds -- afterIds

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
      .flatMap {
        case MatchResult(scrapedItem, esItem) =>
          createAvailabilities(
            networks,
            esItem.id,
            esItem.title.get.headOption.orElse(esItem.original_title).get,
            scrapedItem,
            true
          )
      }

    logger.warn(
      s"Could not find matches for added items: ${addedNotFound}"
    )

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
      .flatMap {
        case MatchResult(scrapedItem, esItem) =>
          createAvailabilities(
            networks,
            esItem.id,
            esItem.title.get.headOption.orElse(esItem.original_title).get,
            scrapedItem,
            false
          )
      }

    logger.warn(
      s"Could not find matches for added items: ${beforeNotFound}"
    )

    logger.info(s"Found ${removedAvailabilities.size} availabilities to remove")

    if (!parsedArgs.dryRun) {
      logger.info(
        s"Saving ${(newAvailabilities ++ removedAvailabilities).size} availabilities"
      )
      saveAvailabilities(newAvailabilities, removedAvailabilities).await()
    } else {
      (newAvailabilities ++ removedAvailabilities).foreach(av => {
        logger.info(s"Would've saved availability: $av")
      })
    }
  }

  protected def saveAvailabilities(
    newAvailabilities: Seq[Availability],
    availabilitiesToRemove: Seq[Availability]
  ): Future[Unit]

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
  ): List[Availability]

  protected def uniqueKey(item: T): String

  override protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: IngestDeltaJobArgs
  ): Future[Unit] = Future.unit
}

trait IngestDeltaJobWithElasticsearch[T <: ScrapedItem] {
  self: IngestDeltaJob[T] =>
  protected def itemSearch: ItemLookup
  protected def itemUpdater: ItemUpdater

  override protected def saveAvailabilities(
    newAvailabilities: Seq[Availability],
    availabilitiesToRemove: Seq[Availability]
  ): Future[Unit] = {
    val newAvailabilityByThingId =
      newAvailabilities
        .flatMap(convertToEsAvailability)
        .groupBy(_._1)
        .mapValues(_.map(_._2))

    val removalAvailabilityByThingId =
      availabilitiesToRemove
        .flatMap(convertToEsAvailability)
        .groupBy(_._1)
        .mapValues(_.map(_._2))

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

      itemSearch
        .lookupItem(Left(itemId), None, materializeJoins = false)
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

            itemUpdater
              .update(
                item.rawItem
                  .copy(availability = Some(availabilitiesToSave.toList))
              )
              .map(Some(_))
        }
    })

    Future.sequence(updates).map(_ => {})
  }

  protected def convertToEsAvailability(
    availability: Availability
  ): Option[(UUID, EsAvailability)] = {
    availability.thingId.map(
      _ -> elasticsearch.EsAvailability(
        network_id = availability.networkId.get,
        region = availability.region.getOrElse("US"),
        start_date = availability.startDate.map(_.toLocalDate),
        end_date = availability.endDate.map(_.toLocalDate),
        offer_type = availability.offerType.getOrElse(OfferType.Rent).toString,
        cost = availability.cost.map(_.toDouble),
        currency = availability.currency,
        presentation_types =
          Some(availability.presentationType.map(_.toString).toList) // TODO FIX
      )
    )
  }
}
