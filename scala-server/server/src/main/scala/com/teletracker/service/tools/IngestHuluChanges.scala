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
import com.teletracker.service.model.tmdb.{Movie, TvShow}
import com.teletracker.service.process.tmdb.TmdbEntityProcessor
import com.teletracker.service.util.Futures._
import com.teletracker.service.util.NetworkCache
import com.teletracker.service.util.execution.SequentialFutures
import com.twitter.inject.app.App
import io.circe.java8._
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.commons.text.similarity.LevenshteinDistance
import java.time.{LocalDate, ZoneOffset}
import java.io.File
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

object IngestHuluChangesMain extends IngestHuluChanges

class IngestHuluChanges extends App {
  val today = LocalDate.now()

  val offset = flag[Int]("offset", 0, "The offset to start at")
  val limit = flag[Int]("limit", -1, "The number of items to process")

  val inputFile = flag[File]("input", "The json file to parse")
  val titleMatchThreshold = flag[Int]("fuzzyThreshold", 15, "X")
  val dryRun = flag[Boolean]("dryRun", true, "X")

  implicit private class ListWithSafeTake[T](l: List[T]) {
    def safeTake(n: Int): List[T] = if (n < 0) l else l.take(n)
  }

  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val networks = injector.instance[NetworkCache]

    val huluNetworkOpt = networks.get().await().find {
      case (_, network) => network.slug.value == "hulu"
    }

    if (huluNetworkOpt.isEmpty) {
      exitOnError(
        new IllegalStateException("Could not find Hulu network from datastore")
      )
    }

    val (_, huluNetwork) = huluNetworkOpt.get

    val client = injector.instance[TmdbClient]
    val processor = injector.instance[TmdbEntityProcessor]
    val thingsDb = injector.instance[ThingsDbAccess]

    val s = Source.fromFile(inputFile())
    try {
      val items =
        parse(s.getLines().mkString("")).flatMap(_.as[List[HuluScrapeItem]])

      items match {
        case Left(value) => value.printStackTrace()
        case Right(items) =>
          SequentialFutures
            .serialize(items.drop(offset()).safeTake(limit()), Some(40 millis))(
              item => {
                item.category.toLowerCase().trim() match {
                  case "film" =>
                    client
                      .searchMovies(item.name)
                      .flatMap(result => {
                        result.results
                          .find(findMatch(_, item))
                          .map(processor.handleMovie)
                          .map(_.map {
                            case (_, thing) =>
                              println(
                                s"Saved ${item.name} with thing ID = ${thing.id.get}"
                              )

                              updateAvailability(
                                huluNetwork,
                                thingsDb,
                                thing,
                                item
                              )
                          })
                          .getOrElse(Future.successful(None))
                      })

                  case _ =>
                    client
                      .searchTv(item.name)
                      .flatMap(result => {
                        result.results
                          .find(findMatch(_, item))
                          .map(processor.handleShow(_, handleSeasons = false))
                          .map(_.map {
                            case (_, thing) =>
                              println(
                                s"Saved ${item.name} with thing ID = ${thing.id.get}"
                              )

                              updateAvailability(
                                huluNetwork,
                                thingsDb,
                                thing,
                                item
                              )
                          })
                          .getOrElse(Future.successful(None))
                      })
                }
              }
            )
            .await()
      }
    } finally {
      s.close()
    }
  }

  private def findMatch(
    movie: Movie,
    item: HuluScrapeItem
  ): Boolean = {
    val titlesEqual = movie.title
      .orElse(movie.original_title)
      .exists(foundTitle => {
        val dist =
          LevenshteinDistance.getDefaultInstance
            .apply(foundTitle.toLowerCase(), item.name.toLowerCase())

        dist <= titleMatchThreshold()
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

  private def findMatch(
    show: TvShow,
    item: HuluScrapeItem
  ): Boolean = {
    val titlesEqual = {
      val dist = LevenshteinDistance.getDefaultInstance
        .apply(show.name.toLowerCase(), item.name.toLowerCase())
      dist <= titleMatchThreshold()
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

  private def updateAvailability(
    huluNetwork: Network,
    thingsDb: ThingsDbAccess,
    thing: Thing,
    scrapeItem: HuluScrapeItem
  ): Future[Option[Availability]] = {
    val start =
      if (scrapeItem.isExpiring) None else Some(scrapeItem.availableLocalDate)
    val end =
      if (scrapeItem.isExpiring) Some(scrapeItem.availableLocalDate) else None

    thingsDb
      .findAvailability(thing.id.get, huluNetwork.id.get)
      .flatMap {
        case Some(availability) =>
          thingsDb.saveAvailability(
            availability.copy(
              isAvailable = start.exists(_.isBefore(today)) || end
                .exists(_.isAfter(today)),
              numSeasons = None,
              startDate = start.map(_.atStartOfDay().atOffset(ZoneOffset.UTC)),
              endDate = end.map(_.atStartOfDay().atOffset(ZoneOffset.UTC))
            )
          )

        case None =>
          thingsDb.saveAvailability(
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
              endDate = end.map(_.atStartOfDay().atOffset(ZoneOffset.UTC)),
              offerType = Some(OfferType.Subscription),
              cost = None,
              currency = None,
              thingId = Some(thing.id.get),
              tvShowEpisodeId = None,
              networkId = Some(huluNetwork.id.get),
              presentationType = Some(PresentationType.HD)
            )
          )
      }
  }
}

case class HuluScrapeItem(
  availableDate: String,
  name: String,
  releaseYear: Option[String],
  notes: String,
  category: String,
  network: String,
  status: String) {

  val availableLocalDate =
    LocalDate.parse(availableDate, DateTimeFormatter.ISO_LOCAL_DATE)

  val isExpiring = status == "Expiring"
}
