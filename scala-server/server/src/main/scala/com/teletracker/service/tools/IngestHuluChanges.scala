package com.teletracker.service.tools

import com.google.inject.Module
import com.teletracker.service.db.model._
import com.teletracker.service.inject.Modules
import com.teletracker.service.model.tmdb.{Movie, TvShow}
import com.teletracker.service.util.Futures._
import com.teletracker.service.util.Lists._
import com.teletracker.service.util.execution.SequentialFutures
import io.circe.generic.auto._
import org.apache.commons.text.similarity.LevenshteinDistance
import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object IngestHuluChanges extends IngestJob[HuluScrapeItem] {

  override protected def modules: Seq[Module] = Modules()

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def runInternal(
    items: List[HuluScrapeItem],
    networks: Set[Network]
  ): Unit = {
    SequentialFutures
      .serialize(items.drop(offset()).safeTake(limit()), Some(40 millis))(
        item => {
          item.category.toLowerCase().trim() match {
            case "film" =>
              tmdbClient
                .searchMovies(item.title)
                .flatMap(result => {
                  result.results
                    .find(findMatch(_, item))
                    .map(tmdbProcessor.handleMovie)
                    .map(_.map {
                      case (_, thing) =>
                        println(
                          s"Saved ${item.title} with thing ID = ${thing.id.get}"
                        )

                        updateAvailability(
                          networks,
                          thing,
                          item
                        )
                    })
                    .getOrElse(Future.successful(None))
                })

            case _ =>
              tmdbClient
                .searchTv(item.title)
                .flatMap(result => {
                  result.results
                    .find(findMatch(_, item))
                    .map(tmdbProcessor.handleShow(_, handleSeasons = false))
                    .map(_.map {
                      case (_, thing) =>
                        println(
                          s"Saved ${item.title} with thing ID = ${thing.id.get}"
                        )

                        updateAvailability(
                          networks,
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

  private def findMatch(
    movie: Movie,
    item: HuluScrapeItem
  ): Boolean = {
    val titlesEqual = movie.title
      .orElse(movie.original_title)
      .exists(foundTitle => {
        val dist =
          LevenshteinDistance.getDefaultInstance
            .apply(foundTitle.toLowerCase(), item.title.toLowerCase())

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
        .apply(show.name.toLowerCase(), item.title.toLowerCase())
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
}

case class HuluScrapeItem(
  availableDate: String,
  title: String,
  releaseYear: Option[String],
  notes: String,
  category: String,
  network: String,
  status: String)
    extends ScrapedItem
