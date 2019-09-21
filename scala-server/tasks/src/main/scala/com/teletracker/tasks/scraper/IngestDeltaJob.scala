package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.tasks.TeletrackerTask
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.Decoder
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.LocalDate
import com.teletracker.common.util.Futures._
import scala.io.Source
import scala.util.control.NonFatal

abstract class IngestDeltaJob[T <: ScrapedItem](implicit decoder: Decoder[T])
    extends TeletrackerTask {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  case class IngestDeltaJobArgs(
    snapshotAfter: URI,
    snapshotBefore: URI,
    offset: Int = 0,
    limit: Int = -1,
    dryRun: Boolean = true,
    titleMatchThreshold: Int = 15,
    mode: MatchMode[T] = new DbLookup[T](thingsDbAccess))
      extends IngestJobArgsLike[T]

  protected val logger = LoggerFactory.getLogger(getClass)

  val today = LocalDate.now()

  protected def storage: Storage
  protected def thingsDbAccess: ThingsDbAccess

  protected def parseMode: ParseMode = AllJson

  override def preparseArgs(args: Args): Unit = parseArgs(args)

  private def parseArgs(args: Map[String, Option[Any]]): IngestDeltaJobArgs = {
    IngestDeltaJobArgs(
      snapshotAfter = args.valueOrThrow[URI]("snapshotAfter"),
      snapshotBefore = args.valueOrThrow[URI]("snapshotBefore"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      dryRun = args.valueOrDefault("dryRun", true)
    )
  }

  override def run(args: Args): Unit = {
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
    val (foundItems, nonMatchedItems) = parsedArgs.mode
      .lookup(
        newItems.toList,
        parsedArgs
      )
      .await()

    foundItems.foreach {
      case (scraped, db) =>
        logger.info(s"${scraped.title} - ${db.id}")
    }

    logger.info(s"ids removed: ${removedIds}")
    logger.info(s"ids added: ${newIds}")
  }

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
