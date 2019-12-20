package com.teletracker.tasks.scraper

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.Network
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.TeletrackerTask
import com.teletracker.tasks.scraper.matching.MatchMode
import com.teletracker.tasks.scraper.model.{MatchResult, NonMatchResult}
import io.circe.Codec
import io.circe.syntax._
import javax.inject.Inject
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

// Base class for processing a bunch of items
abstract class BaseIngestJob[
  T <: ScrapedItem,
  IngestJobArgsType <: IngestJobArgsLike
](
)(implicit executionContext: ExecutionContext,
  codec: Codec[T])
    extends TeletrackerTask {

  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _
  @Inject
  private[this] var s3: S3Client = _

  protected val logger = LoggerFactory.getLogger(getClass)

  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC
  protected lazy val today = LocalDate.now()
  protected lazy val now = OffsetDateTime.now()

  protected val missingItemsFile = new File(
    s"${today}_${getClass.getSimpleName}-missing-items.json"
  )
  protected val matchItemsFile = new File(
    s"${today}_${getClass.getSimpleName}-match-items.json"
  )

  protected val missingItemsWriter = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(missingItemsFile))
  )

  protected val matchingItemsWriter = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(matchItemsFile))
  )

  private val _artifacts: mutable.ListBuffer[File] = {
    val a = new mutable.ListBuffer[File]()
    a ++= List(matchItemsFile, missingItemsFile)
    a
  }

  protected def artifacts: List[File] = _artifacts.toList

  protected def registerArtifact(file: File): Unit = synchronized {
    _artifacts += file
  }

  protected def matchMode: MatchMode

  protected def processMode(args: IngestJobArgsType): ProcessMode

  postrun { _ =>
    missingItemsWriter.flush()
    matchingItemsWriter.flush()
  }

  postrun(args => {
    if (args.valueOrDefault("uploadArtifacts", true)) {

      artifacts.foreach(artifact => {
        s3.putObject(
          PutObjectRequest
            .builder()
            .bucket(teletrackerConfig.data.s3_bucket)
            .key(
              s"task-output/${getClass.getSimpleName}/$now/${artifact.getName}"
            )
            .build(),
          artifact.toPath
        )
      })
    }
  })

  private lazy val scheduledPool = Executors.newSingleThreadScheduledExecutor()

  private def sleep(delay: FiniteDuration) = {
    val p = Promise[Unit]()
    scheduledPool.schedule(new Runnable {
      override def run(): Unit = p.success(())
    }, delay.toMillis, TimeUnit.MILLISECONDS)
    p.future
  }

  protected def processAll(
    items: AsyncStream[T],
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): AsyncStream[(List[MatchResult[T]], List[T])] = {
    processMode(args) match {
      case Serial(perBatchSleep) =>
        items
          .drop(args.offset)
          .safeTake(args.limit)
          .map(sanitizeItem)
          .mapF(item => {
            for {
              res <- processSingle(item, networks, args)
              _ <- perBatchSleep.map(sleep).getOrElse(Future.unit)
            } yield res

          })

      case Parallel(parallelism, perBatchSleep) =>
        items
          .drop(args.offset)
          .safeTake(args.limit)
          .grouped(parallelism)
          .mapF(batch => {
            for {
              res <- processBatch(batch.toList, networks, args)
              _ <- perBatchSleep.map(sleep).getOrElse(Future.unit)
            } yield res
          })
    }
  }

  protected def processBatch(
    items: List[T],
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): Future[(List[MatchResult[T]], List[T])] = {
    val filteredAndSanitized = items.filter(shouldProcessItem).map(sanitizeItem)
    if (filteredAndSanitized.nonEmpty) {
      matchMode
        .lookup(
          filteredAndSanitized,
          args
        )
        .flatMap {
          case (things, nonMatchedItems) =>
            handleNonMatches(args, nonMatchedItems).flatMap(fallbackMatches => {
              val allItems = things ++ fallbackMatches
                .map(_.toMatchResult)

              val stillMissing = (filteredAndSanitized
                .map(_.title.toLowerCase())
                .toSet -- things
                .map(_.esItem.title.get.head.toLowerCase())
                .toSet) -- fallbackMatches
                .map(_.originalScrapedItem.title.toLowerCase())
                .toSet

              val missingItems = filteredAndSanitized
                .filter(
                  item => stillMissing.contains(item.title.toLowerCase())
                )

              writeMissingItems(
                missingItems
              )

              logger.info(
                s"Successfully found matches for ${allItems.size} out of ${items.size} items."
              )

              if (args.dryRun) {
                writeMatchingItems(allItems)
              }

              handleMatchResults(allItems, networks, args).map(_ => {
                allItems -> missingItems
              })
            })
        }
    } else {
      Future.successful(Nil -> Nil)
    }
  }

  protected def processSingle(
    item: T,
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): Future[(List[MatchResult[T]], List[T])] = {
    processBatch(List(item), networks, args)
  }

  protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): Future[Unit]

  protected def shouldProcessItem(item: T): Boolean = true

  protected def sanitizeItem(item: T): T = identity(item)

  protected def handleNonMatches(
    args: IngestJobArgsType,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = Future.successful(Nil)

  protected def writeMissingItems(items: List[T]): Unit = {
    items.foreach(item => {
      missingItemsWriter.println(item.asJson.noSpaces)
    })
  }

  protected def writeMatchingItems(items: List[MatchResult[T]]): Unit = {
    items
      .map(_.toSerializable)
      .foreach(
        matchingItem =>
          matchingItemsWriter.println(matchingItem.asJson.noSpaces)
      )
  }

  sealed trait ProcessMode
  case class Serial(perBatchSleep: Option[FiniteDuration] = None)
      extends ProcessMode
  case class Parallel(
    parallelism: Int,
    perBatchSleep: Option[FiniteDuration] = None)
      extends ProcessMode
}
