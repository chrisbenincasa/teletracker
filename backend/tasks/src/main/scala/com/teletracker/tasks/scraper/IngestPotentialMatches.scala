package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.model.scraping
import com.teletracker.common.model.scraping.{MatchResult, ScrapedItem}
import com.teletracker.common.tasks.TeletrackerTask.RawArgs
import com.teletracker.common.util.Folds
import com.teletracker.tasks.scraper.model.PotentialInput
import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import io.circe.{Codec, Encoder}
import java.net.URI
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

// Job that takes in a hand-filtered potential match file output and imports the items
abstract class IngestPotentialMatches[T <: ScrapedItem: Codec]
    extends IngestJob[PotentialInput[T]]

@JsonCodec
case class IngestPotentialMatchesDeltaArgs(
  snapshotAfter: URI,
  snapshotBefore: Option[URI],
  potentialMappings: URI,
  override val offset: Int = 0,
  override val limit: Int = -1,
  dryRun: Boolean = true,
  titleMatchThreshold: Int = 15,
  itemIdFilter: Option[UUID] = None,
  externalIdFilter: Option[String] = None,
  processBatchSleep: Option[FiniteDuration] = None,
  sleepBetweenWriteMs: Option[Long] = None,
  override val parallelism: Option[Int])
    extends IngestJobArgsLike
    with IngestDeltaJobArgsLike {
  override val deltaSizeThreshold: Double = 0.0
  override val disableDeltaSizeCheck: Boolean = true
}

//abstract class IngestPotentialMatchesDelta[T <: ScrapedItem: Codec](
//  deps: IngestDeltaJobDependencies)
//    extends IngestDeltaJob[T](deps) {
//  protected lazy val potentialMappings: mutable.Map[String, PotentialInput[T]] =
//    mutable.HashMap.empty[String, PotentialInput[T]]
//
//  override def runInternal(): Unit = {
//    val potentialMappingsSource =
//      deps.sourceRetriever.getSource(args.potentialMappings)
//
//    try {
//      loadMappings(potentialMappingsSource.getLines())
//    } finally {
//      potentialMappingsSource.close()
//    }
//
//    super.runInternal()
//  }
//
//  override protected def processBatch(
//    items: List[T],
//    networks: Set[StoredNetwork],
//    args: IngestPotentialMatchesDeltaArgs
//  ): Future[(List[MatchResult[T]], List[T])] = {
//    val (haveMatch, doesntHaveMatch) =
//      items.partition(
//        item => uniqueKey(item).exists(potentialMappings.isDefinedAt)
//      )
//
//    val itemsByEsId = haveMatch
//      .map(item => potentialMappings(uniqueKey(item).get).potential.id -> item)
//      .toMap
//
//    deps.itemLookup
//      .lookupItemsByIds(itemsByEsId.keySet)
//      .map(esItemMap => {
//        val (matchResults, missingEsItems) =
//          itemsByEsId.foldLeft(Folds.list2Empty[MatchResult[T], T]) {
//            case ((matches, misses), (esId, scrapedItem)) =>
//              esItemMap.get(esId).flatten match {
//                case Some(value) =>
//                  (matches :+ scraping
//                    .MatchResult(scrapedItem, value)) -> misses
//                case None => matches -> (misses :+ scrapedItem)
//              }
//          }
//
//        writeMissingItems(doesntHaveMatch ++ missingEsItems)
//
//        matchResults -> (doesntHaveMatch ++ missingEsItems)
//      })
//  }
//
//  protected def loadMappings(lines: Iterator[String]): Unit = {
//    potentialMappings ++= new IngestJobParser()
//      .stream[PotentialInput[T]](lines)
//      .flatMap {
//        case Left(value) =>
//          logger.warn("Could not parse line of potential matches", value)
//          None
//
//        case Right(value) => uniqueKey(value.scraped).map(_ -> value)
//      }
//  }
//}
