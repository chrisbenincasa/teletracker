package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.util.Folds
import com.teletracker.tasks.scraper.matching.ElasticsearchLookup
import com.teletracker.tasks.scraper.model.{MatchResult, PotentialInput}
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.util.SourceRetriever
import io.circe.{Codec, Encoder}
import io.circe.generic.semiauto.deriveEncoder
import java.net.URI
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future

// Job that takes in a hand-filtered potential match file output and imports the items
abstract class IngestPotentialMatches[T <: ScrapedItem: Codec]
    extends IngestJob[PotentialInput[T]]

case class IngestPotentialMatchesDeltaArgs(
  snapshotAfter: URI,
  snapshotBefore: URI,
  potentialMappings: URI,
  offset: Int = 0,
  limit: Int = -1,
  dryRun: Boolean = true,
  titleMatchThreshold: Int = 15,
  thingIdFilter: Option[UUID] = None,
  perBatchSleepMs: Option[Int] = None)
    extends IngestJobArgsLike
    with IngestDeltaJobArgsLike

abstract class IngestPotentialMatchesDelta[T <: ScrapedItem: Codec](
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestDeltaJobLike[T, IngestPotentialMatchesDeltaArgs](
      elasticsearchLookup
    ) {
  implicit override protected def typedArgsEncoder
    : Encoder[IngestPotentialMatchesDeltaArgs] = deriveEncoder

  override def preparseArgs(args: Args): IngestPotentialMatchesDeltaArgs =
    parseArgs(args)

  protected def parseArgs(
    args: Map[String, Option[Any]]
  ): IngestPotentialMatchesDeltaArgs = {
    IngestPotentialMatchesDeltaArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.valueOrThrow[URI]("snapshotBefore"),
      potentialMappings = args.valueOrThrow[URI]("potentialMappings"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true),
      thingIdFilter = args.value[UUID]("thingIdFilter"),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs")
    )
  }

  protected lazy val potentialMappings: mutable.Map[String, PotentialInput[T]] =
    mutable.HashMap.empty[String, PotentialInput[T]]

  override def runInternal(args: Args): Unit = {
    val parsedArgs = parseArgs(args)

    val sourceRetriever = new SourceRetriever(s3)

    val potentialMappingsSource =
      sourceRetriever.getSource(parsedArgs.potentialMappings)

    try {
      loadMappings(potentialMappingsSource.getLines())
    } finally {
      potentialMappingsSource.close()
    }

    super.runInternal(args)
  }

  override protected def processBatch(
    items: List[T],
    networks: Set[StoredNetwork],
    args: IngestPotentialMatchesDeltaArgs
  ): Future[(List[MatchResult[T]], List[T])] = {
    val (haveMatch, doesntHaveMatch) =
      items.partition(item => potentialMappings.isDefinedAt(uniqueKey(item)))

    val itemsByEsId = haveMatch
      .map(item => potentialMappings(uniqueKey(item)).potential.id -> item)
      .toMap

    itemLookup
      .getItemsById(itemsByEsId.keySet)
      .map(esItemMap => {
        val (matchResults, missingEsItems) =
          itemsByEsId.foldLeft(Folds.list2Empty[MatchResult[T], T]) {
            case ((matches, misses), (esId, scrapedItem)) =>
              esItemMap.get(esId).flatten match {
                case Some(value) =>
                  (matches :+ MatchResult(scrapedItem, value)) -> misses
                case None => matches -> (misses :+ scrapedItem)
              }
          }

        writeMissingItems(doesntHaveMatch ++ missingEsItems)

        matchResults -> (doesntHaveMatch ++ missingEsItems)
      })
  }

  protected def loadMappings(lines: Iterator[String]): Unit = {
    potentialMappings ++= new IngestJobParser()
      .stream[PotentialInput[T]](lines)
      .flatMap {
        case Left(value) =>
          logger.warn("Could not parse line of potential matches", value)
          None

        case Right(value) => Some(uniqueKey(value.scraped) -> value)
      }
  }
}
