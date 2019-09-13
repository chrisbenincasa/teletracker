package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  EsAvailability,
  ItemSearch,
  ItemUpdater
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.util.SourceRetriever
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintStream}
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
  offset: Int = 0,
  limit: Int = -1,
  titleMatchThreshold: Int = 15,
  dryRun: Boolean = true)
    extends IngestJobArgsLike

abstract class IngestJob[T <: ScrapedItem](
  implicit decoder: Decoder[T],
  encoder: Encoder[T])
    extends TeletrackerTask {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected val logger = LoggerFactory.getLogger(getClass)

  protected val today = LocalDate.now()
  protected val missingItemsFile = new File(
    s"${today}_${getClass.getSimpleName}-missing-items.json"
  )
  protected val missingItemsWriter = new PrintStream(
    new BufferedOutputStream(new FileOutputStream(missingItemsFile))
  )

  protected def tmdbClient: TmdbClient
  protected def tmdbProcessor: TmdbEntityProcessor
  protected def thingsDb: ThingsDbAccess
  protected def s3: S3Client
  protected def networkCache: NetworkCache

  protected def networkNames: Set[String]

  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC

  protected def parseMode: ParseMode = AllJson
  protected def matchMode: MatchMode[T] = new DbLookup[T](thingsDb)

  protected def processMode(args: IngestJobArgs): ProcessMode = Serial

  override type TypedArgs = IngestJobArgs

  implicit override protected def typedArgsEncoder: Encoder[IngestJobArgs] =
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
      dryRun = args.valueOrDefault("dryRun", true)
    )
  }

  override def runInternal(args: Map[String, Option[Any]]): Unit = {
    val parsedArgs = parseArgs(args)
    val network = getNetworksOrExit()
    implicit val listDec = implicitly[Decoder[List[T]]]

    logger.info(s"Starting ingest of ${networkNames} content")

    val source = new SourceRetriever(s3).getSource(parsedArgs.inputFile)

    try {
      val items = new IngestJobParser().parse[T](source.getLines(), parseMode)

      items match {
        case Left(value) =>
          value.printStackTrace()
          throw value

        case Right(items) =>
          processAll(
            items,
            network,
            parsedArgs
          )
      }
    } catch {
      case NonFatal(e) =>
        throw e
    } finally {
      source.close()
      missingItemsWriter.flush()
      missingItemsWriter.close()
    }
  }

  protected def processAll(
    items: List[T],
    networks: Set[Network],
    args: IngestJobArgs
  ): Unit = {
    processMode(args) match {
      case Serial =>
        SequentialFutures
          .serialize(
            items.drop(args.offset).safeTake(args.limit).map(sanitizeItem),
            Some(40 millis)
          )(
            processSingle(_, networks, args)
          )
          .await()

      case Parallel(parallelism) =>
        SequentialFutures
          .batchedIterator(
            items.drop(args.offset).safeTake(args.limit).iterator,
            parallelism
          )(batch => {
            processBatch(batch.toList, networks, args)
          })
          .await()
    }
  }

  protected def processBatch(
    items: List[T],
    networks: Set[Network],
    args: IngestJobArgs
  ): Future[Unit] = {
    matchMode
      .lookup(
        items.filter(shouldProcessItem).map(sanitizeItem),
        args
      )
      .flatMap {
        case (things, nonMatchedItems) =>
          handleNonMatches(args, nonMatchedItems).flatMap(fallbackMatches => {
            val allItems = things ++ fallbackMatches
              .map(fb => fb.amendedItem -> fb.thingRaw)
            val stillMissing = (items
              .map(_.title)
              .toSet -- things.map(_._1.title).toSet) -- fallbackMatches
              .map(_.originalItem.title)
              .toSet

            writeMissingItems(
              items.filter(item => stillMissing.contains(item.title))
            )

            logger.info(
              s"Successfully found matches for ${allItems.size} out of ${items.size} items."
            )

            Future
              .sequence {
                allItems.map {
                  case (item, thing) =>
                    val availabilityFut =
                      createAvailabilities(networks, thing, item)

                    if (args.dryRun) {
                      availabilityFut.map(avs => {
                        avs.foreach(
                          av =>
                            logger.debug(s"Would've saved availability: $av")
                        )
                        avs
                      })
                    } else {
                      availabilityFut.flatMap(avs => {
                        val availabilitiesGrouped = avs
                          .filter(_.thingId.isDefined)
                          .groupBy(_.thingId.get)

                        SequentialFutures
                          .serialize(
                            availabilitiesGrouped.toList.grouped(50).toList
                          )(
                            batch =>
                              Future
                                .sequence(
                                  batch.map(Function.tupled(saveAvailabilities))
                                )
                                .map(_ => {})
                          )
                      })
                    }

                }
              }
              .map(_ => {})
          })
      }
  }

  protected def saveAvailabilities(
    itemId: UUID,
    availabilities: Seq[Availability]
  ): Future[Unit] = {
    thingsDb.saveAvailabilities(availabilities)
  }

  protected def shouldProcessItem(item: T): Boolean = true

  protected def sanitizeItem(item: T): T = identity(item)

  case class NonMatchResult(
    amendedItem: T,
    originalItem: T,
    thingRaw: ThingRaw)

  protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[T]
  ): Future[List[NonMatchResult]] = Future.successful(Nil)

  protected def processSingle(
    item: T,
    networks: Set[Network],
    args: IngestJobArgs
  ): Future[Unit] = {
    processBatch(List(item), networks, args)
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
    scrapeItem: T
  ): Future[Seq[Availability]] = {
    val start =
      if (scrapeItem.isExpiring) None else scrapeItem.availableLocalDate
    val end =
      if (scrapeItem.isExpiring) scrapeItem.availableLocalDate else None

    SequentialFutures
      .serialize(networks.toSeq)(
        network => {
          thingsDb
            .findAvailability(thing.id, network.id.get)
            .map {
              case Seq() =>
                presentationTypes.toSeq.map(pres => {
                  Availability(
                    None,
                    isAvailable = isAvailable(scrapeItem, today),
                    region = Some("US"),
                    numSeasons = None,
                    startDate =
                      start.map(_.atStartOfDay().atOffset(networkTimeZone)),
                    endDate =
                      end.map(_.atStartOfDay().atOffset(networkTimeZone)),
                    offerType = Some(OfferType.Subscription),
                    cost = None,
                    currency = None,
                    thingId = Some(thing.id),
                    tvShowEpisodeId = None,
                    networkId = Some(network.id.get),
                    presentationType = Some(pres)
                  )
                })

              case availabilities =>
                // TODO(christian) - find missing presentation types
                availabilities.map(
                  _.copy(
                    isAvailable = isAvailable(scrapeItem, today),
                    numSeasons = None,
                    startDate =
                      start.map(_.atStartOfDay().atOffset(networkTimeZone)),
                    endDate =
                      end.map(_.atStartOfDay().atOffset(networkTimeZone))
                  )
                )

//                thingsDb.saveAvailabilities(newAvs).map(_ => newAvs)
            }
        }
      )
      .map(_.flatten)
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

  protected def writeMissingItems(items: List[T]): Unit = {
    items.foreach(item => {
      missingItemsWriter.println(item.asJson.noSpaces)
    })
  }

  sealed trait ProcessMode
  case object Serial extends ProcessMode
  case class Parallel(parallelism: Int) extends ProcessMode
}

trait IngestJobWithElasticsearch[T <: ScrapedItem] { self: IngestJob[T] =>
  protected def itemSearch: ItemSearch
  protected def itemUpdater: ItemUpdater

  override protected def saveAvailabilities(
    itemId: UUID,
    availabilities: Seq[Availability]
  ): Future[Unit] = {
    val distinctAvailabilities = availabilities
      .flatMap(convertToEsAvailability)
      .map(_._2)
      .groupBy(av => (av.network_id, av.region, av.offer_type))
      .flatMap {
        case (key, values) => combinePresentationTypes(values).map(key -> _)
      }
      .values

    itemSearch
      .lookupItem(Left(itemId), None, materializeJoins = false)
      .flatMap {
        case None => Future.successful(None)
        case Some(item) =>
          logger.info(
            s"Updating availability for item id = ${item.rawItem.id}"
          )

          val newAvailabilities = item.rawItem.availability match {
            case Some(value) =>
              val duplicatesRemoved = value.filterNot(availability => {
                distinctAvailabilities.exists(
                  EsAvailability.availabilityEquivalent(_, availability)
                )
              })

              duplicatesRemoved ++ distinctAvailabilities

            case None =>
              distinctAvailabilities
          }

          itemUpdater
            .update(
              item.rawItem
                .copy(availability = Some(newAvailabilities.toList))
            )
            .map(Some(_))
      }
  }

  protected def combinePresentationTypes(
    availabilities: Seq[EsAvailability]
  ): Option[EsAvailability] = {
    if (availabilities.isEmpty) {
      None
    } else {
      Some(availabilities.reduce((l, r) => {
        val combined = l.presentation_types
          .getOrElse(Nil) ++ r.presentation_types.getOrElse(Nil)
        l.copy(presentation_types = Some(combined.distinct))
      }))
    }
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
          availability.presentationType.map(_.toString).map(List(_))
      )
    )
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

  lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(LocalDate.parse(_, DateTimeFormatter.ISO_LOCAL_DATE))

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean
  def isTvShow: Boolean
  def thingType = {
    if (isMovie) {
      ThingType.Movie
    } else if (isTvShow) {
      ThingType.Show
    } else {
      throw new IllegalArgumentException("")
    }
  }
}
