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
import io.circe.parser._
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
    mode: MatchMode = DbLookup)

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

  protected def findMatch[W](
    tmdbItem: W,
    item: T,
    titleMatchThreshold: Int
  )(implicit watchable: TmdbWatchable[W]
  ): Boolean = {
    val titlesEqual = watchable
      .title(tmdbItem)
      .exists(foundTitle => {
        val dist =
          LevenshteinDistance.getDefaultInstance
            .apply(foundTitle.toLowerCase(), item.title.toLowerCase())

        dist <= titleMatchThreshold
      })

    val releaseYearEqual = watchable
      .releaseYear(tmdbItem)
      .exists(tmdbReleaseYear => {
        item.releaseYear
          .exists(
            ry => (tmdbReleaseYear - 1 to tmdbReleaseYear + 1).contains(ry)
          )
      })

    titlesEqual && releaseYearEqual
  }

  protected def findMatch(
    thingRaw: ThingRaw,
    item: T,
    titleMatchThreshold: Int
  ) = {
    val dist =
      LevenshteinDistance.getDefaultInstance
        .apply(thingRaw.name.toLowerCase(), item.title.toLowerCase())

    dist <= titleMatchThreshold
  }

  protected def mutateItem(item: T): T = identity(item)

  sealed trait MatchMode {
    def lookup(
      items: List[T],
      args: IngestJobArgs
    ): Future[List[(T, ThingRaw)]]
  }

  case object TmdbLookup extends MatchMode {
    override def lookup(
      items: List[T],
      args: IngestJobArgs
    ): Future[List[(T, ThingRaw)]] = {
      SequentialFutures.serialize(items)(lookupSingle(_, args)).map(_.flatten)
    }

    private def lookupSingle(
      item: T,
      args: IngestJobArgs
    ): Future[Option[(T, ThingRaw)]] = {
      val search = if (item.isMovie) {
        tmdbClient.searchMovies(item.title).map(_.results.map(Left(_)))
      } else if (item.isTvShow) {
        tmdbClient.searchTv(item.title).map(_.results.map(Right(_)))
      } else {
        Future.failed(new IllegalArgumentException)
      }

      search
        .flatMap(results => {
          results
            .find(findMatch(_, item, args.titleMatchThreshold))
            .map(x => tmdbProcessor.handleWatchable(x))
            .map(_.flatMap {
              case ProcessSuccess(_, thing: ThingRaw) =>
                logger.info(
                  s"Saved ${item.title} with thing ID = ${thing.id}"
                )

                Future.successful(Some(item -> thing))

              case ProcessSuccess(_, _) =>
                logger.error("Unexpected result")
                Future.successful(None)

              case ProcessFailure(error) =>
                logger.error("Error handling movie", error)
                Future.successful(None)
            })
            .getOrElse(Future.successful(None))
        })
    }
  }

  case object DbLookup extends MatchMode {
    override def lookup(
      items: List[T],
      args: IngestJobArgs
    ): Future[List[(T, ThingRaw)]] = {
      val (withReleaseYear, withoutReleaseYear) =
        items.partition(_.releaseYear.isDefined)

      if (withoutReleaseYear.nonEmpty) {
        println(s"${withoutReleaseYear.size} things without release year")
      }

      val itemsBySlug = withReleaseYear
        .map(item => {
          Slug(item.title, item.releaseYear.get.toInt) -> item
        })
        .toMap

      thingsDb
        .findThingsBySlugsRaw(itemsBySlug.keySet)
        .map(thingBySlug => {
          itemsBySlug.toList.sortBy(_._1.value).flatMap {
            case (itemSlug, item) =>
              thingBySlug
                .get(itemSlug)
                .flatMap(thingRaw => {
                  val dist = LevenshteinDistance.getDefaultInstance
                    .apply(
                      thingRaw.name.toLowerCase().trim,
                      item.title.toLowerCase().trim
                    )

                  if (dist > args.titleMatchThreshold) {
                    println(
                      s"Bad match for ${item.title} => ${thingRaw.name} - DIST: $dist"
                    )

                    None
                  } else {
                    Some(item -> thingRaw)
                  }
                })
          }
        })
    }
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
