package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{Availability, Network, ThingRaw}
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.TeletrackerTask
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.{LocalDate, ZoneOffset}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.NetworkCache
import java.util.UUID
import scala.io.Source
import scala.util.control.NonFatal

case class IngestDeltaJobArgs(
  snapshotAfter: URI,
  snapshotBefore: URI,
  offset: Int = 0,
  limit: Int = -1,
  dryRun: Boolean = true,
  titleMatchThreshold: Int = 15,
  thingIdFilter: Option[UUID] = None)
    extends IngestJobArgsLike

abstract class IngestDeltaJob[T <: ScrapedItem](implicit decoder: Decoder[T])
    extends TeletrackerTask {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected val logger = LoggerFactory.getLogger(getClass)

  val today = LocalDate.now()

  protected def storage: Storage
  protected def thingsDbAccess: ThingsDbAccess

  protected def networkNames: Set[String]
  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC
  protected def networkCache: NetworkCache

  protected def parseMode: ParseMode = AllJson
  protected def matchMode: MatchMode[T] = new DbLookup[T](thingsDbAccess)

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
      thingIdFilter = args.value[UUID]("thingIdFilter")
    )
  }

  override def runInternal(args: Args): Unit = {
    val networks = getNetworksOrExit()

    val parsedArgs = parseArgs(args)
    val afterSource =
      new SourceRetriever(storage).getSource(parsedArgs.snapshotAfter)
    val beforeSource =
      new SourceRetriever(storage).getSource(parsedArgs.snapshotBefore)

    val after = parseSource(afterSource)
    val before = parseSource(beforeSource)

    val afterById = after.map(a => uniqueKey(a) -> a).toMap
    val beforeById = before.map(a => uniqueKey(a) -> a).toMap

    val newIds = afterById.keySet -- beforeById.keySet
    val removedIds = beforeById.keySet -- afterById.keySet

    val newItems = newIds.flatMap(afterById.get)
    val (foundItems, nonMatchedItems) = matchMode
      .lookup(
        newItems.toList,
        parsedArgs
      )
      .await()

    val newAvailabilities = foundItems
      .filter {
        case (_, thing) => parsedArgs.thingIdFilter.forall(_ == thing.id)
      }
      .flatMap {
        case (scrapedItem, thing) =>
          createAvailabilities(networks, thing, scrapedItem, true)
      }

    logger.warn(
      s"Could not find matches for added items: ${nonMatchedItems}"
    )

    val removedItems = removedIds.flatMap(beforeById.get)
    val (foundRemovals, nonMatchedRemovals) = matchMode
      .lookup(
        removedItems.toList,
        parsedArgs
      )
      .await()

    val removedAvailabilities = foundRemovals
      .filter {
        case (_, thing) => parsedArgs.thingIdFilter.forall(_ == thing.id)
      }
      .flatMap {
        case (scrapedItem, thing) =>
          createAvailabilities(networks, thing, scrapedItem, false)
      }

    logger.warn(
      s"Could not find matches for added items: ${nonMatchedRemovals}"
    )

    logger.info(s"Found ${removedAvailabilities.size} availabilities to remove")

    if (!parsedArgs.dryRun) {
      logger.info(
        s"Saving ${(newAvailabilities ++ removedAvailabilities).size} availabilities"
      )
      thingsDbAccess
        .saveAvailabilities(
          newAvailabilities ++ removedAvailabilities
        )
        .await()
    } else {
      (newAvailabilities ++ removedAvailabilities).foreach(av => {
        logger.info(s"Would've saved availability: $av")
      })
    }
  }

  protected def getNetworksOrExit(): Set[Network] = {
    val foundNetworks = networkCache
      .get()
      .await()
      .collect {
        case (_, network) if networkNames.contains(network.slug.value) =>
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
    networks: Set[Network],
    thing: ThingRaw,
    scrapedItem: T,
    isAvailable: Boolean
  ): List[Availability]

  protected def uniqueKey(item: T): String

  private def parseSource(source: Source): List[T] = {
    try {
      val items = new IngestJobParser().parse[T](source.getLines(), parseMode)

      items match {
        case Left(value) =>
          value.printStackTrace()
          throw value

        case Right(items) =>
          items
      }
    } catch {
      case NonFatal(e) =>
        throw e
    } finally {
      source.close()
    }
  }
}
