package com.teletracker.service.tools

import com.google.inject.Module
import com.teletracker.service.db.ThingsDbAccess
import com.teletracker.service.db.model.{
  Availability,
  Network,
  OfferType,
  PresentationType,
  Thing
}
import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.Modules
import com.teletracker.service.process.tmdb.TmdbEntityProcessor
import com.teletracker.service.util.NetworkCache
import com.teletracker.service.util.Futures._
import com.teletracker.service.util.execution.SequentialFutures
import io.circe.parser._
import io.circe.Decoder
import java.io.File
import java.time.{LocalDate, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.control.NonFatal

abstract class IngestJob[T <: ScrapedItem](implicit decoder: Decoder[T])
    extends com.twitter.inject.app.App {

  val today = LocalDate.now()

  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The number of items to process")

  val inputFile = flag[File]("input", "The json file to parse")
  val titleMatchThreshold = flag[Int]("fuzzyThreshold", 15, "X")
  val dryRun = flag[Boolean]("dryRun", true, "X")

  lazy val tmdbClient = injector.instance[TmdbClient]
  lazy val tmdbProcessor = injector.instance[TmdbEntityProcessor]
  lazy val thingsDb = injector.instance[ThingsDbAccess]

  override protected def modules: Seq[Module] = Modules()

  protected def networkNames: Set[String]

  protected def runInternal(
    items: List[T],
    network: Set[Network]
  ): Unit

  override protected def run(): Unit = {
    val network = getNetworksOrExit()
    implicit val listDec = implicitly[Decoder[List[T]]]

    val source = Source.fromFile(inputFile())

    try {
      val items =
        parse(source.getLines().mkString("")).flatMap(_.as[List[T]])

      items match {
        case Left(value) =>
          value.printStackTrace()
          exitOnError(value)

        case Right(items) =>
          runInternal(items, network)
      }
    } catch {
      case NonFatal(e) =>
        exitOnError(e)
    } finally {
      source.close()
    }
  }

  protected def getNetworksOrExit(): Set[Network] = {
    val networks = injector.instance[NetworkCache]

    val foundNetworks = networks
      .get()
      .await()
      .collect {
        case (_, network) if networkNames.contains(network.slug.value) =>
          network
      }
      .toSet

    if (networkNames.diff(foundNetworks.map(_.slug.value)).nonEmpty) {
      exitOnError(
        new IllegalStateException(
          s"""Could not find all networks "${networkNames}" network from datastore"""
        )
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
            .findAvailability(thing.id.get, network.id.get)
            .flatMap {
              case Seq() =>
                val avs =
                  Seq(PresentationType.SD, PresentationType.HD).map(pres => {
                    Availability(
                      None,
                      isAvailable = start.exists(_.isBefore(today)) || end
                        .exists(_.isAfter(today)),
                      region = Some("US"),
                      numSeasons = None,
                      startDate = Some(
                        scrapeItem.availableLocalDate
                          .atStartOfDay()
                          .atOffset(ZoneOffset.UTC)
                      ),
                      endDate =
                        end.map(_.atStartOfDay().atOffset(ZoneOffset.UTC)),
                      offerType = Some(OfferType.Subscription),
                      cost = None,
                      currency = None,
                      thingId = Some(thing.id.get),
                      tvShowEpisodeId = None,
                      networkId = Some(network.id.get),
                      presentationType = Some(pres)
                    )
                  })

                thingsDb.insertAvailabilities(avs)

              case availabilities =>
                val newAvs = availabilities.map(
                  _.copy(
                    isAvailable = start.exists(_.isBefore(today)) || end
                      .exists(_.isAfter(today)),
                    numSeasons = None,
                    startDate =
                      start.map(_.atStartOfDay().atOffset(ZoneOffset.UTC)),
                    endDate = end.map(_.atStartOfDay().atOffset(ZoneOffset.UTC))
                  )
                )

                thingsDb.saveAvailabilities(newAvs).map(_ => newAvs)
            }
        }
      )
      .map(_.flatten)
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
}
