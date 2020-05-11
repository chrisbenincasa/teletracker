package com.teletracker.tasks.scraper

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsExternalId,
  EsItem
}
import com.teletracker.common.elasticsearch.{
  AvailabilityQueryHelper,
  ItemLookup,
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
import com.teletracker.tasks.scraper.matching.{
  CustomElasticsearchLookup,
  ElasticsearchExternalIdLookup,
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.model.MatchResult
import com.teletracker.tasks.util.{SourceRetriever, SourceWriter}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Codec, Decoder, Encoder}
import javax.inject.Inject
import org.elasticsearch.common.xcontent.{XContentHelper, XContentType}
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
  perBatchSleepMs: Option[Int],
  enableExternalIdMatching: Boolean = true,
  reimport: Boolean = false)
    extends IngestJobArgsLike

abstract class IngestJob[T <: ScrapedItem](
  implicit protected val codec: Codec[T])
    extends BaseIngestJob[T, IngestJobArgs]()(
      scala.concurrent.ExecutionContext.Implicits.global,
      codec
    ) {

  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected def itemLookup: ItemLookup
  protected def itemUpdater: ItemUpdater
  protected def s3: S3Client
  protected def networkCache: NetworkCache

  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  protected def parseMode: ParseMode

  protected def externalSources: List[ExternalSource]

  @Inject
  private[this] var externalIdLookup: ElasticsearchExternalIdLookup.Factory = _
  @Inject
  private[this] var elasticsearchLookup: ElasticsearchLookup = _
  @Inject
  private[this] var availabilityQueryHelper: AvailabilityQueryHelper = _

  protected def lookupMethod(args: TypedArgs): LookupMethod[T] = {
    val externalIdMatchers = if (args.enableExternalIdMatching) {
      externalSources.map(externalSource => {
        externalIdLookup.createOpt[T](
          externalSource,
          (item: T) => getExternalIds(item).get(externalSource)
        )
      })
    } else {
      Nil
    }

    new CustomElasticsearchLookup[T](
      externalIdMatchers :+ elasticsearchLookup.toMethod[T]
    )
  }

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
      perBatchSleepMs = args.value[Int]("perBatchSleepMs"),
      enableExternalIdMatching =
        args.valueOrDefault("enableExternalIdMatching", true),
      reimport = args.valueOrDefault("reimport", false)
    )
  }

  override def runInternal(args: Map[String, Option[Any]]): Unit = {
    val parsedArgs = parseArgs(args)
    val network = getNetworksOrExit()
    implicit val listDec = implicitly[Decoder[List[T]]]

    logger.info("Running preprocess phase")
    preprocess(parsedArgs, args)

    if (parsedArgs.reimport) {
      val sources = externalSources.map(_.getName)
      val matchingNetworks =
        network.filter(net => sources.contains(net.slug.value))
      if (!parsedArgs.dryRun) {
        logger.info("Reimport mode. Deleting existing availability.")
        availabilityQueryHelper.deleteAvailabilityForNetworks(matchingNetworks)
      } else {
        val query =
          availabilityQueryHelper.getDeleteAvailabilityForNetworksQuery(
            matchingNetworks
          )
        val jsonQuery =
          XContentHelper.toXContent(query, XContentType.JSON, true)
        logger.info(
          s"Would've deleted all availability for networks: ${sources}: ${jsonQuery.utf8ToString()}"
        )
      }
    }

    logger.info(s"Starting ingest of ${externalSources} content")

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
    networks: Set[StoredNetwork],
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
    networks: Set[StoredNetwork],
    args: IngestJobArgs
  ): Future[Unit] = {
    val itemsWithNewAvailability = results.flatMap {
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

        val newExternalIds = esItem.externalIdsGrouped ++ getExternalIds(item)

        val newItem = esItem.copy(
          availability = Some(newAvailabilities.toList),
          external_ids = EsExternalId.fromMap(newExternalIds)
        )

        if (args.dryRun && esItem != newItem) {
          logger.info(
            s"Would've updated id = ${newItem.id}:\n ${diff(esItem.asJson, newItem.asJson).asJson.spaces2}"
          )
        }

        if (esItem != newItem) {
          Some(newItem)
        } else {
          None
        }
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
                  .map(
                    item =>
                      itemUpdater.update(item).map(_ => {}).recover {
                        case NonFatal(e) =>
                          logger
                            .error(s"Failed to update item id = ${item.id}", e)
                      }
                  )
              )
              .map(_ => {})
        )
        .map(_ => {})
    } else {
      Future.successful {
        logger.info(
          s"Would've saved ${itemsWithNewAvailability.size} items with availability: ${}"
        )
      }
    }
  }

  protected def getNetworksOrExit(): Set[StoredNetwork] = {
    val foundNetworks = networkCache
      .getAllNetworks()
      .await()
      .collect {
        case network
            if externalSources.map(_.getName).contains(network.slug.value) =>
          network
      }
      .toSet

    if (externalSources
          .map(_.getName)
          .toSet
          .diff(foundNetworks.map(_.slug.value))
          .nonEmpty) {
      throw new IllegalStateException(
        s"""Could not find all networks "${externalSources}" network from datastore"""
      )
    }

    foundNetworks
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

  protected def getExternalIds(item: T): Map[ExternalSource, String] = Map.empty

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
        case NonFatal(e) => logger.error("Error writing missing-items file", e)
      }

      try {
        new SourceWriter(s3).writeFile(
          uri.resolve(s"${uri.getPath}/potential-matches.txt"),
          potentialMatchFile.toPath
        )
      } catch {
        case NonFatal(e) =>
          logger.error("Error writing potential-matches file", e)
      }
    })
  }
}

trait ScrapedItem {
  def availableDate: Option[String]
  def title: String
  def releaseYear: Option[Int]
  def category: Option[String]
  def network: String
  def status: String
  def externalId: Option[String]
  def description: Option[String]

  def actualItemId: Option[UUID] = None

  lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean
  def isTvShow: Boolean
  def thingType: Option[ItemType] = {
    if (isMovie) {
      Some(ItemType.Movie)
    } else if (isTvShow) {
      Some(ItemType.Show)
    } else {
      None
    }
  }

  def url: Option[String] = None

  def numSeasonsAvailable: Option[Int] = None
}
