package com.teletracker.tasks

import com.google.cloud.storage.{BlobId, Storage}
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{Movie, TvShow}
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.process.tmdb.TmdbEntityProcessor.{
  ProcessFailure,
  ProcessSuccess
}
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Lists._
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.execution.SequentialFutures
import io.circe.Decoder
import io.circe.parser._
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
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

  case class IngestJobArgs(
    inputFile: URI,
    offset: Int = 0,
    limit: Int = -1,
    titleMatchThreshold: Int = 15,
    dryRun: Boolean = true)

  override def preparseArgs(args: Args): Unit = parseArgs(args)

  private def parseArgs(args: Map[String, Option[Any]]): IngestJobArgs = {
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

    logger.info("Starting ingest of HBO content")

    val source = getSource(parsedArgs.inputFile)

    try {
      val items =
        parse(source.getLines().mkString("")).flatMap(_.as[List[T]])

      items match {
        case Left(value) =>
          value.printStackTrace()
          throw value

        case Right(items) =>
          processAll(
            items,
            network,
            parsedArgs.offset,
            parsedArgs.limit,
            parsedArgs.titleMatchThreshold
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
    offset: Int,
    limit: Int,
    titleMatchThreshold: Int
  ): Unit = {
    SequentialFutures
      .serialize(items.drop(offset).safeTake(limit), Some(40 millis))(
        processSingle(_, networks, titleMatchThreshold)
      )
      .await()
  }

  protected def processSingle(
    item: T,
    networks: Set[Network],
    titleMatchThreshold: Int
  ): Future[Unit] = {
    if (item.isMovie) {
      tmdbClient
        .searchMovies(item.title)
        .flatMap(result => {
          result.results
            .find(findMatch(_, item, titleMatchThreshold))
            .map(tmdbProcessor.handleMovie)
            .map(_.map {
              case ProcessSuccess(_, thing) =>
                logger.info(
                  s"Saved ${item.title} with thing ID = ${thing.id}"
                )

                updateAvailability(
                  networks,
                  thing,
                  item
                )

              case ProcessFailure(error) =>
                logger.error("Error handling movie", error)
                Future.successful(Seq.empty)
            })
            .map(_.map(_ => {}))
            .getOrElse(Future.successful(None))
        })
    } else if (item.isTvShow) {
      tmdbClient
        .searchTv(item.title)
        .flatMap(result => {
          result.results
            .find(findMatch(_, item, titleMatchThreshold))
            .map(tmdbProcessor.handleShow(_, handleSeasons = false))
            .map(_.map {
              case ProcessSuccess(_, thing) =>
                logger.info(
                  s"Saved ${item.title} with thing ID = ${thing.id}"
                )

                updateAvailability(
                  networks,
                  thing,
                  item
                )

              case ProcessFailure(error) =>
                logger.error("Error saving show", error)
                Future.successful(Seq.empty)
            })
            .map(_.map(_ => {}))
            .getOrElse(Future.unit)
        })
    } else {
      Future.successful(println(s"Unrecognized item type for: $item"))
    }
  }

  private def getSource(uri: URI): Source = {
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

  protected def updateAvailability(
    networks: Set[Network],
    thing: Thing,
    scrapeItem: T
  ): Future[Seq[Availability]] = {
    val start =
      if (scrapeItem.isExpiring) None else Some(scrapeItem.availableLocalDate)
    val end =
      if (scrapeItem.isExpiring) Some(scrapeItem.availableLocalDate) else None

    SequentialFutures
      .serialize(networks.toSeq)(
        network => {
          thingsDb
            .findAvailability(thing.id, network.id.get)
            .flatMap {
              case Seq() =>
                val avs =
                  presentationTypes.toSeq.map(pres => {
                    Availability(
                      None,
                      isAvailable = start.exists(_.isBefore(today)) || end
                        .exists(_.isAfter(today)),
                      region = Some("US"),
                      numSeasons = None,
                      startDate = Some(
                        scrapeItem.availableLocalDate
                          .atStartOfDay()
                          .atOffset(networkTimeZone)
                      ),
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

                thingsDb.insertAvailabilities(avs)

              case availabilities =>
                // TODO(christian) - find missing presentation types
                val newAvs = availabilities.map(
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

                thingsDb.saveAvailabilities(newAvs).map(_ => newAvs)
            }
        }
      )
      .map(_.flatten)
  }

  protected def findMatch(
    movie: Movie,
    item: T,
    titleMatchThreshold: Int
  ): Boolean = {
    val titlesEqual = movie.title
      .orElse(movie.original_title)
      .exists(foundTitle => {
        val dist =
          LevenshteinDistance.getDefaultInstance
            .apply(foundTitle.toLowerCase(), item.title.toLowerCase())

        dist <= titleMatchThreshold
      })

    val releaseYearEqual = movie.release_date
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_))
      .exists(ld => {
        item.releaseYear
          .map(_.trim.toInt)
          .exists(ry => (ld.getYear - 1 to ld.getYear + 1).contains(ry))
      })

    titlesEqual && releaseYearEqual
  }

  protected def findMatch(
    show: TvShow,
    item: T,
    titleMatchThreshold: Int
  ): Boolean = {
    val titlesEqual = {
      val dist = LevenshteinDistance.getDefaultInstance
        .apply(show.name.toLowerCase(), item.title.toLowerCase())
      dist <= titleMatchThreshold
    }

    val releaseYearEqual = show.first_air_date
      .filter(_.nonEmpty)
      .map(LocalDate.parse(_))
      .exists(ld => {
        item.releaseYear
          .map(_.trim.toInt)
          .exists(ry => (ld.getYear - 1 to ld.getYear + 1).contains(ry))
      })

    titlesEqual && releaseYearEqual
  }
}

trait ScrapedItem {
  def availableDate: String
  def title: String
  def releaseYear: Option[String]
  def category: String
  def network: String
  def status: String

  lazy val availableLocalDate: LocalDate =
    LocalDate.parse(availableDate, DateTimeFormatter.ISO_LOCAL_DATE)

  lazy val isExpiring: Boolean = status == "Expiring"

  def isMovie: Boolean
  def isTvShow: Boolean
}
