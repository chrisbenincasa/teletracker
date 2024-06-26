package com.teletracker.tasks.scraper

import com.teletracker.common.availability.{CrawlerInfo, NetworkAvailability}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  PresentationType,
  SupportedNetwork
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.inject.SingleThreaded
import com.teletracker.common.model.scraping._
import com.teletracker.common.tasks.TeletrackerTask.JsonableArgs
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.ArgParser
import com.teletracker.common.util.{AsyncStream, NetworkCache, OpenDateRange}
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatcher,
  ElasticsearchFallbackMatcherOptions,
  LookupMethod
}
import io.circe.Codec
import io.circe.syntax._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.{
  BufferedOutputStream,
  File,
  FileOutputStream,
  OutputStream,
  PrintStream
}
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.control.NonFatal

// Base class for processing a bunch of items
abstract class BaseIngestJob[
  T <: ScrapedItem,
  IngestJobArgsType <: IngestJobArgsLike: JsonableArgs: ArgParser
](
  networkCache: NetworkCache
)(implicit protected val executionContext: ExecutionContext,
  protected val codec: Codec[T])
    extends TypedTeletrackerTask[IngestJobArgsType] {

  @Inject
  private[this] var teletrackerConfig: TeletrackerConfig = _
  @Inject
  private[this] var s3: S3Client = _
  @Inject
  protected var elasticsearchFallbackMatcher
    : ElasticsearchFallbackMatcher.Factory = _
  @Inject
  @SingleThreaded
  protected var scheduledExecutor: ScheduledExecutorService = _

  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC
  protected lazy val today: LocalDate = LocalDate.now()
  protected lazy val now: OffsetDateTime = OffsetDateTime.now()
  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  // The networks this job generates availability for
  protected def supportedNetworks: Set[SupportedNetwork]

  // The external source that pulled data for this job. External IDs should be unique within this namespace.
  protected def externalSource: ExternalSource

  // The type of items scraped from this data source.
  protected def scrapeItemType: ScrapeCatalogType

  protected val (missingItemsWriter, missingItemsFile) = newPrintStream(
    s"${today}_${getClass.getSimpleName}-missing-items.json"
  )

  protected val (matchingItemsWriter, matchItemsFile) = newPrintStream(
    s"${today}_${getClass.getSimpleName}-match-items.json"
  )

  protected val (potentialMatchesWriter, potentialMatchFile) = newPrintStream(
    s"${today}_${getClass.getSimpleName}-potential-matches.json"
  )

  protected val (errorFileWriter, errorFile) = newPrintStream(
    s"${today}_${getClass.getSimpleName}-process-errors.json"
  )

  private val totalItemsCounter = new AtomicInteger()

  protected def newArtifact(filePath: String): Artifact = {
    Artifact.tupled.apply(newPrintStream(filePath))
  }

  protected def newPrintStream(filePath: String): (PrintStream, File) = {
    val f = new File(filePath)
    (newPrintStream(f), f)
  }

  protected def newPrintStream(file: File): PrintStream =
    new PrintStream(
      new BufferedOutputStream(
        new FileOutputStream(
          file
        )
      )
    )

  private val _artifacts: mutable.ListBuffer[Artifact] = {
    val a = new mutable.ListBuffer[Artifact]()
    a ++= List(
      Artifact(matchingItemsWriter, matchItemsFile),
      Artifact(missingItemsWriter, missingItemsFile),
      Artifact(potentialMatchesWriter, potentialMatchFile),
      Artifact(errorFileWriter, errorFile)
    )
    a
  }

  case class Artifact(
    os: OutputStream,
    file: File)

  protected def artifacts: List[Artifact] = _artifacts.toList

  protected def registerArtifact(artifact: Artifact): Unit =
    _artifacts.synchronized {
      if (!_artifacts.exists(
            _.file.getAbsolutePath == artifact.file.getAbsolutePath
          )) {
        _artifacts += artifact
      }
    }

  protected def defaultParallelism: Int = 16

  protected def lookupMethod(): LookupMethod[T]

  postrun(args => {
    artifacts.foreach {
      case Artifact(os, _) =>
        os.flush()
        os.close()
    }

    if (args.valueOrDefault("uploadArtifacts", true)) {
      artifacts
        .map(_.file)
        .foreach(artifact => {
          s3.putObject(
            PutObjectRequest
              .builder()
              .bucket(teletrackerConfig.data.s3_bucket)
              .key(
                s"$remoteArtifactPrefix/${artifact.getName}"
              )
              .build(),
            artifact.toPath
          )
        })
    }
  })

  protected def processAll(
    items: AsyncStream[T],
    networks: Set[StoredNetwork]
  ): AsyncStream[(List[MatchResult[T]], List[T])] = {
    var count = 0L
    items
      .drop(args.offset)
      .safeTake(args.limit)
      .grouped(args.parallelism.getOrElse(defaultParallelism))
      .map(_.toList)
      .delayedMapF(
        args.processBatchSleep.getOrElse(0 millis),
        scheduledExecutor
      )(processBatch(_, networks, args))
      .withEffect {
        case (m, nm) =>
          count += m.size + nm.size
          if (count % 1000 == 0) {
            logger.info(s"Processed $count items so far.")
          }
      }
  }

  protected def processBatch(
    items: List[T],
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): Future[(List[MatchResult[T]], List[T])] = {
    val filteredAndSanitized = items.filter(shouldProcessItem).map(sanitizeItem)
    if (filteredAndSanitized.nonEmpty) {
      lookupMethod()
        .apply(
          filteredAndSanitized,
          args
        )
        .flatMap {
          case (exactMatchResults, nonMatchedItems) =>
            findPotentialMatches(args, nonMatchedItems).flatMap(
              fallbackMatches => {
                val originalItems = filteredAndSanitized
                  .map(itemUniqueIdentifier)
                  .toSet

                val firstPhaseFinds = exactMatchResults
                  .map(_.scrapedItem)
                  .map(itemUniqueIdentifier)
                  .toSet

                val secondPhaseFinds = fallbackMatches
                  .map(_.originalScrapedItem)
                  .map(itemUniqueIdentifier)
                  .toSet

                val stillMissing = originalItems -- firstPhaseFinds -- secondPhaseFinds

                val missingItems = filteredAndSanitized
                  .filter(
                    item => stillMissing.contains(itemUniqueIdentifier(item))
                  )

                writePotentialMatches(fallbackMatches.map {
                  case NonMatchResult(_, originalScrapedItem, esItem) =>
                    esItem -> originalScrapedItem
                })

                writeMissingItems(
                  missingItems
                )

                logger.info(
                  s"Successfully found matches for ${exactMatchResults.size} out of ${items.size} items."
                )

                writeMatchingItems(exactMatchResults)

                handleMatchResults(exactMatchResults, networks, args).map(_ => {
                  exactMatchResults -> missingItems
                })
              }
            )
        }
        .andThen {
          case Success(_) =>
            logger.info(
              s"Processed ${totalItemsCounter.addAndGet(items.size)} items, so far."
            )
        }
        .recoverWith {
          case NonFatal(e) =>
            logger.error(
              "Got fatal error when processing batch. Calling handler",
              e
            )

            onProcessBatchError(items).map(_ => Nil -> Nil)
        }
    } else {
      Future.successful(Nil -> Nil)
    }
  }

  protected def onProcessBatchError(items: List[T]): Future[Unit] = {
    Future {
      items.map(_.asJson.noSpaces).foreach(errorFileWriter.println)
    }
  }

  protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[StoredNetwork],
    args: IngestJobArgsType
  ): Future[Unit]

  protected def shouldProcessItem(item: T): Boolean = true

  protected def sanitizeItem(item: T): T = identity(item)

  protected def itemUniqueIdentifier(item: T): String = item.title.toLowerCase()

  protected def findPotentialMatches(
    args: IngestJobArgsType,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    elasticsearchFallbackMatcher
      .create(getElasticsearchFallbackMatcherOptions)
      .handleNonMatches(
        args,
        nonMatches
      )
  }

  protected def getElasticsearchFallbackMatcherOptions
    : ElasticsearchFallbackMatcherOptions =
    ElasticsearchFallbackMatcherOptions(
      requireTypeMatch = true,
      sourceJobName = getClass.getSimpleName
    )

  protected def writeMissingItems(items: List[T]): Unit = synchronized {
    items.foreach(item => {
      missingItemsWriter.println(item.asJson.noSpaces)
    })
  }

  protected def writeMatchingItems(items: List[MatchResult[T]]): Unit =
    synchronized {
      items
        .map(_.toSerializable)
        .foreach(
          matchingItem =>
            matchingItemsWriter.println(matchingItem.asJson.noSpaces)
        )
    }

  protected def writePotentialMatches(
    potentialMatches: Iterable[(EsItem, T)]
  ): Unit = synchronized {
    potentialMatches
      .map(Function.tupled(PotentialMatch.forEsItem))
      .foreach(potentialMatch => {
        potentialMatchesWriter.println(
          potentialMatch.asJson.noSpaces
        )
      })
  }

  protected def getNetworksOrExit(): Set[StoredNetwork] = {
    val foundNetworks = networkCache
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

  protected def createAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapeItem: T
  ): Seq[EsAvailability]

  protected def getContext: Option[IngestJobContext] = None
}

trait BaseSubscriptionNetworkAvailability[
  T <: ScrapedItem,
  Args <: IngestJobArgsLike] {
  self: BaseIngestJob[T, Args] =>

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapeItem: T
  ): Seq[EsAvailability] = {
    val start =
      if (scrapeItem.isExpiring) None else scrapeItem.availableLocalDate
    val end =
      if (scrapeItem.isExpiring) scrapeItem.availableLocalDate else None

    val availabilitiesByNetwork = item.availabilityGrouped

    val unaffectedNetworks = availabilitiesByNetwork.keySet -- networks
      .map(
        _.id
      )

    networks.toList.flatMap(network => {
      availabilitiesByNetwork.get(network.id) match {
        case Some(existingAvailabilities) =>
          existingAvailabilities.map(_.copy(start_date = start, end_date = end))

        case None =>
          NetworkAvailability.forSubscriptionNetwork(
            network,
            availableWindow = OpenDateRange(start, end),
            presentationTypes = presentationTypes,
            numSeasonAvailable = scrapeItem.numSeasonsAvailable,
            updateSource = Some(getClass.getSimpleName),
            crawlerInfo = getContext.flatMap(_.crawlerInfo)
          )
      }
    }) ++ unaffectedNetworks.toList.flatMap(availabilitiesByNetwork.get).flatten
  }
}

case class IngestJobContext(crawlerInfo: Option[CrawlerInfo])

trait SubscriptionNetworkAvailability[T <: ScrapedItem]
    extends BaseSubscriptionNetworkAvailability[T, IngestJobArgs] {
  self: IngestJob[T] =>
}

trait SubscriptionNetworkDeltaAvailability[T <: ScrapedItem]
    extends BaseSubscriptionNetworkAvailability[T, IngestDeltaJobArgs] {
  self: IngestDeltaJob[T] =>

  override protected def createDeltaAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapedItem: T,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    createAvailabilities(networks, item, scrapedItem).toList
  }
}
