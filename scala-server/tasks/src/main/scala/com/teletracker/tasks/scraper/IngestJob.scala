package com.teletracker.tasks.scraper

import com.google.cloud.storage.{BlobId, Storage}
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.TmdbWatchable
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessSuccess
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.{NetworkCache, Slug}
import com.teletracker.tasks.scraper.IngestJobParser.{AllJson, ParseMode}
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskApp}
import io.circe.Decoder
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}
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

trait IngestJobArgsLike[T <: ScrapedItem] {
  val offset: Int
  val limit: Int
  val titleMatchThreshold: Int
  val dryRun: Boolean
  val mode: MatchMode[T]
}

abstract class IngestJob[T <: ScrapedItem](implicit decoder: Decoder[T])
    extends TeletrackerTask {

  implicit protected val execCtx =
    scala.concurrent.ExecutionContext.Implicits.global

  protected val logger = LoggerFactory.getLogger(getClass)

  val today = LocalDate.now()

  protected def tmdbClient: TmdbClient
  protected def tmdbProcessor: TmdbEntityProcessor
  protected def thingsDb: ThingsDbAccess
  protected def storage: Storage
  protected def networkCache: NetworkCache

  protected def networkNames: Set[String]

  protected def presentationTypes: Set[PresentationType] =
    Set(PresentationType.SD, PresentationType.HD)

  protected def networkTimeZone: ZoneOffset = ZoneOffset.UTC

  protected def parseMode: ParseMode = AllJson

  protected def processMode(args: IngestJobArgs): ProcessMode = Serial

  case class IngestJobArgs(
    inputFile: URI,
    offset: Int = 0,
    limit: Int = -1,
    titleMatchThreshold: Int = 15,
    dryRun: Boolean = true,
    mode: MatchMode[T] = new DbLookup(thingsDb))
      extends IngestJobArgsLike[T]

  override def preparseArgs(args: Args): Unit = parseArgs(args)

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

  override def run(args: Map[String, Option[Any]]): Unit = {
    val parsedArgs = parseArgs(args)
    val network = getNetworksOrExit()
    implicit val listDec = implicitly[Decoder[List[T]]]

    logger.info(s"Starting ingest of ${networkNames} content")

    val source = getSource(parsedArgs.inputFile)

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
            items.drop(args.offset).safeTake(args.limit).map(mutateItem),
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
    args.mode
      .lookup(
        items,
        args
      )
      .flatMap {
        case things =>
          Future
            .sequence {
              things.map {
                case (item, thing) =>
                  val availabilityFut =
                    createAvailabilities(networks, thing, item)

                  if (args.dryRun) {
                    availabilityFut.map(avs => {
                      avs.foreach(
                        av => logger.info(s"Would've saved availability: $av")
                      )
                      avs
                    })
                  } else {
                    availabilityFut.flatMap(avs => {
                      SequentialFutures
                        .serialize(avs.grouped(50).toList)(batch => {
                          Future
                            .sequence(batch.map(thingsDb.insertAvailability))
                        })
                    })
                  }

              }
            }
            .map(_ => {})
      }
  }

  protected def processSingle(
    item: T,
    networks: Set[Network],
    args: IngestJobArgs
  ): Future[Unit] = {
    processBatch(List(item), networks, args)
  }

  final protected def getSource(uri: URI): Source = {
    uri.getScheme match {
      case "gs" =>
        Source.fromBytes(
          storage
            .get(BlobId.of(uri.getHost, uri.getPath.stripPrefix("/")))
            .getContent()
        )
      case "file" =>
        Source.fromFile(uri)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupposed file scheme: ${uri.getScheme}"
        )
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
//                val avs =
                presentationTypes.toSeq.map(pres => {
                  Availability(
                    None,
                    isAvailable = start.exists(_.isBefore(today)) || end
                      .exists(_.isAfter(today)),
                    region = Some("US"),
                    numSeasons = None,
                    startDate = scrapeItem.availableLocalDate
                      .map(_.atStartOfDay().atOffset(networkTimeZone)),
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

//                thingsDb.insertAvailabilities(avs)

              case availabilities =>
                // TODO(christian) - find missing presentation types
                availabilities.map(
                  _.copy(
                    isAvailable = start.exists(_.isBefore(today)) || end
                      .exists(_.isAfter(today)),
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

  sealed trait ProcessMode
  case object Serial extends ProcessMode
  case class Parallel(parallelism: Int) extends ProcessMode
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
}
