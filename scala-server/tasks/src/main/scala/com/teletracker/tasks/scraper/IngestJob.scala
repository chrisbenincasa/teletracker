package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  EsItem,
  ItemLookup,
  ItemSearch,
  ItemUpdater
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.{AsyncStream, NetworkCache}
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.Functions._
import com.teletracker.tasks.TeletrackerTaskApp
import com.teletracker.tasks.scraper.IngestJobParser.ParseMode
import com.teletracker.tasks.scraper.matching.MatchMode
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Codec, Decoder, Encoder}
import software.amazon.awssdk.services.s3.S3Client
import java.io.File
import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.util.control.NonFatal

abstract class IngestJobApp[T <: IngestJob[_]: Manifest]
    extends TeletrackerTaskApp[T] {
  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The number of items to process")

  val inputFile = flag[File]("input", "The json file to parse")
  val titleMatchThreshold = flag[Int]("fuzzyThreshold", 15, "X")
  val dryRun = flag[Boolean]("dryRun", true, "X")
}

trait IngestJobArgsLike {
  val offset: Int
  val limit: Int
  val titleMatchThreshold: Int
  val dryRun: Boolean
}

case class IngestJobArgs(
  inputFile: URI,
  offset: Int,
  limit: Int,
  titleMatchThreshold: Int,
  dryRun: Boolean,
  parallelism: Int,
  sourceLimit: Int,
  perBatchSleepMs: Option[Int])
    extends IngestJobArgsLike

abstract class IngestJob[T <: ScrapedItem](
  implicit protected val codec: Codec[T])
    extends BaseIngestJob[T, IngestJobArgs]()(
      scala.concurrent.ExecutionContext.Implicits.global,
      codec
    ) {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected def itemLookup: ItemLookup
  protected def itemUpdater: ItemUpdater
  protected def s3: S3Client
  protected def networkCache: NetworkCache

  protected def networkNames: Set[String]

  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  protected def parseMode: ParseMode
  protected def matchMode: MatchMode

  protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(args.parallelism, args.perBatchSleepMs.map(_ millis))

  protected def outputLocation(
    args: TypedArgs,
    rawArgs: Args
  ): Option[URI] = None
  protected def getAdditionalOutputFiles: Seq[(File, String)] = Seq()

  override type TypedArgs = IngestJobArgs

  implicit override protected def typedArgsEncoder: Encoder[TypedArgs] =
    deriveEncoder[IngestJobArgs]

  override def preparseArgs(args: Args): TypedArgs = parseArgs(args)

  final protected def parseArgs(
    args: Map[String, Option[Any]]
  ): IngestJobArgs = {
    IngestJobArgs(
      inputFile = args.valueOrThrow[URI]("inputFile"),
      offset = args.valueOrDefault("offset", 0),
      limit = args.valueOrDefault("limit", -1),
      titleMatchThreshold = args.valueOrDefault("fuzzyThreshold", 15),
      dryRun = args.valueOrDefault("dryRun", true),
      parallelism = args.valueOrDefault("parallelism", 32),
      sourceLimit = args.valueOrDefault("sourceLimit", -1),
      perBatchSleepMs = args.value[Int]("perBatchSleepMs")
    )
  }

  override def runInternal(args: Map[String, Option[Any]]): Unit = {
    val parsedArgs = parseArgs(args)
    val network = getNetworksOrExit()
    implicit val listDec = implicitly[Decoder[List[T]]]

    logger.info("Running preprocess phase")
    preprocess(parsedArgs, args)

    logger.info(s"Starting ingest of ${networkNames} content")

    new SourceRetriever(s3)
      .getSourceStream(parsedArgs.inputFile)
      .safeTake(parsedArgs.sourceLimit)
      .foreach(processSource(_, network, parsedArgs, args))
  }

  protected def preprocess(
    args: IngestJobArgs,
    rawArgs: Args
  ): Unit = {}

  private def processSource(
    source: Source,
    networks: Set[Network],
    parsedArgs: IngestJobArgs,
    rawArgs: Args
  ): Unit = {
    try {
      parseMode match {
        case IngestJobParser.JsonPerLine =>
          new IngestJobParser()
            .asyncStream[T](source.getLines())
            .flatMapOption {
              case Left(value) =>
                logger.warn("Could not parse line", value)
                None
              case Right(value) =>
                Some(value)
            }
            .throughApply(processAll(_, networks, parsedArgs))
            .force
            .await()

        case IngestJobParser.AllJson =>
          new IngestJobParser().parse[T](source.getLines(), parseMode) match {
            case Left(value) =>
              value.printStackTrace()
              throw value

            case Right(items) =>
              processAll(
                AsyncStream.fromSeq(items),
                networks,
                parsedArgs
              ).force.await()
          }
      }
    } catch {
      case NonFatal(e) =>
        throw e
    } finally {
      source.close()
      missingItemsWriter.flush()
      missingItemsWriter.close()

      matchingItemsWriter.flush()
      matchingItemsWriter.close()

      uploadResultFiles(parsedArgs, rawArgs)
    }
  }

  override protected def handleMatchResults(
    results: List[MatchResult[T]],
    networks: Set[Network],
    args: IngestJobArgs
  ): Future[Unit] = {
    val itemsWithNewAvailability = results.map {
      case MatchResult(item, esItem) =>
        val availabilities =
          createAvailabilities(
            networks,
            esItem,
            item
          )

        val newAvailabilities = esItem.availability match {
          case Some(value) =>
            val duplicatesRemoved = value.filterNot(availability => {
              availabilities.exists(
                EsAvailability.availabilityEquivalent(_, availability)
              )
            })

            duplicatesRemoved ++ availabilities

          case None =>
            availabilities
        }

        esItem.copy(availability = Some(newAvailabilities.toList))
    }

    if (!args.dryRun) {
      SequentialFutures
        .serialize(
          itemsWithNewAvailability.grouped(50).toList
        )(
          batch =>
            Future
              .sequence(
                batch
                  .map(itemUpdater.update)
              )
              .map(_ => {})
        )
        .map(_ => {})
    } else {
      Future.unit
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
    item: EsItem,
    scrapeItem: T
  ): Seq[EsAvailability] = {
    val start =
      if (scrapeItem.isExpiring) None else scrapeItem.availableLocalDate
    val end =
      if (scrapeItem.isExpiring) scrapeItem.availableLocalDate else None

    networks.toSeq.map(network => {
      item.availability
        .getOrElse(Nil)
        .find(_.network_id == network.id.get) match {
        case Some(existingAvailability) =>
          existingAvailability.copy(
            start_date = start,
            end_date = end
          )

        case None =>
          EsAvailability(
            network_id = network.id.get,
            region = "US",
            start_date = start,
            end_date = end,
            // TODO: This isn't always correct, let jobs configure offer, cost, currency
            offer_type = OfferType.Subscription.toString,
            cost = None,
            currency = None,
            presentation_types = Some(presentationTypes.map(_.toString).toList)
          )
      }
    })
  }

  protected def isAvailable(
    item: T,
    today: LocalDate
  ): Boolean = {
    val start =
      if (item.isExpiring) None else item.availableLocalDate
    val end =
      if (item.isExpiring) item.availableLocalDate else None

    (start.isEmpty && end.isEmpty) ||
    start.exists(_.isBefore(today)) ||
    end.exists(_.isAfter(today))
  }

  protected def uploadResultFiles(
    parsedArgs: TypedArgs,
    rawArgs: Args
  ): Unit = {
    outputLocation(parsedArgs, rawArgs).foreach(uri => {
      try {
        new SourceWriter(s3).writeFile(
          uri.resolve(s"${uri.getPath}/match-items.txt"),
          matchItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing match-items file", e)
      }

      try {
        new SourceWriter(s3).writeFile(
          uri.resolve(s"${uri.getPath}/missing-items.txt"),
          missingItemsFile.toPath
        )
      } catch {
        case NonFatal(e) => logger.error("Error writing match-items file", e)
      }
    })
  }
}

trait ScrapedItem {
  def availableDate: Option[String]
  def title: String
  def releaseYear: Option[Int]
  def category: String
  def network: String
  def status: String
  def externalId: Option[String]

  def actualItemId: Option[UUID] = None

  lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean
  def isTvShow: Boolean
  def thingType: Option[ThingType] = {
    if (isMovie) {
      Some(ThingType.Movie)
    } else if (isTvShow) {
      Some(ThingType.Show)
    } else {
      None
    }
  }
}
