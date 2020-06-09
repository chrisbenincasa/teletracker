package com.teletracker.tasks.scraper

import com.teletracker.common.tasks.{TeletrackerTask, TypedTeletrackerTask}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{OfferType, PresentationType}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import com.teletracker.common.model.scraping.{
  MatchResult,
  NonMatchResult,
  PotentialMatch,
  ScrapeItemType,
  ScrapedItem
}
import com.teletracker.common.tasks.TeletrackerTask.JsonableArgs
import com.teletracker.common.util.AsyncStream
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
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

// Base class for processing a bunch of items
abstract class BaseIngestJob[
  T <: ScrapedItem,
  IngestJobArgsType <: IngestJobArgsLike: JsonableArgs
](
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

  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC
  protected lazy val today: LocalDate = LocalDate.now()
  protected lazy val now: OffsetDateTime = OffsetDateTime.now()
  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  protected def scrapeItemType: ScrapeItemType

  protected val missingItemsFile = new File(
    s"${today}_${getClass.getSimpleName}-missing-items.json"
  )
  protected val matchItemsFile = new File(
    s"${today}_${getClass.getSimpleName}-match-items.json"
  )
  protected val potentialMatchFile = new File(
    s"${today}_${getClass.getSimpleName}-potential-matches.json"
  )

  protected val missingItemsWriter = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(missingItemsFile))
  )

  protected val matchingItemsWriter = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(matchItemsFile))
  )

  protected val potentialMatchesWriter = new PrintStream(
    new BufferedOutputStream(
      new FileOutputStream(
        potentialMatchFile
      )
    )
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

  protected def lookupMethod(): LookupMethod[T]

  protected def processMode(): ProcessMode

  postrun { _ =>
    missingItemsWriter.flush()
    missingItemsWriter.close()
    matchingItemsWriter.flush()
    matchingItemsWriter.close()
    potentialMatchesWriter.flush()
    potentialMatchesWriter.close()
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
    networks: Set[StoredNetwork]
  ): AsyncStream[(List[MatchResult[T]], List[T])] = {
    processMode() match {
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
      lookupMethod()
        .apply(
          filteredAndSanitized,
          args
        )
        .flatMap {
          case (exactMatchResults, nonMatchedItems) =>
            handleNonMatches(args, nonMatchedItems).flatMap(fallbackMatches => {
              val allItems = exactMatchResults ++ fallbackMatches
                .map(_.toMatchResult)

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
                  item => stillMissing.contains(item.title.toLowerCase())
                )

              writeMissingItems(
                missingItems
              )

              logger.info(
                s"Successfully found matches for ${allItems.size} out of ${items.size} items."
              )

              writeMatchingItems(exactMatchResults)

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

  protected def itemUniqueIdentifier(item: T): String = item.title.toLowerCase()

  protected def handleNonMatches(
    args: IngestJobArgsType,
    nonMatches: List[T]
  ): Future[List[NonMatchResult[T]]] = {
    elasticsearchFallbackMatcher
      .create(getElasticsearchFallbackMatcherOptions)
      .handleNonMatches(
        args,
        nonMatches
      )
      .map(results => {
        writePotentialMatches(results.map {
          case result => result.esItem -> result.originalScrapedItem
        })

        // Don't return yet...
        Nil
      })
  }

  protected def getElasticsearchFallbackMatcherOptions =
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

  protected def createAvailabilities(
    networks: Set[StoredNetwork],
    item: EsItem,
    scrapeItem: T
  ): Seq[EsAvailability] = {
    val start =
      if (scrapeItem.isExpiring) None else scrapeItem.availableLocalDate
    val end =
      if (scrapeItem.isExpiring) scrapeItem.availableLocalDate else None

    val availabilitiesByNetwork = item.availabilityGrouped

    val unaffectedNetworks = availabilitiesByNetwork.keySet -- networks.map(
      _.id
    )

    networks.toList.flatMap(network => {
      availabilitiesByNetwork.get(network.id) match {
        case Some(existingAvailabilities) =>
          existingAvailabilities.map(_.copy(start_date = start, end_date = end))

        case None =>
          presentationTypes
            .map(_.toString)
            .toList
            .map(presentationType => {
              EsAvailability(
                network_id = network.id,
                network_name = Some(network.name),
                region = "US",
                start_date = start,
                end_date = end,
                // TODO: This isn't always correct, let jobs configure offer, cost, currency
                offer_type = OfferType.Subscription.toString,
                cost = None,
                currency = None,
                presentation_type = Some(presentationType),
                links = None,
                num_seasons_available = scrapeItem.numSeasonsAvailable
              )
            })
      }
    }) ++ unaffectedNetworks.toList.flatMap(availabilitiesByNetwork.get).flatten
  }

  sealed trait ProcessMode
  case class Serial(perBatchSleep: Option[FiniteDuration] = None)
      extends ProcessMode
  case class Parallel(
    parallelism: Int,
    perBatchSleep: Option[FiniteDuration] = None)
      extends ProcessMode
}
