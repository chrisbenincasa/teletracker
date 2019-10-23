package com.teletracker.tasks.elasticsearch

import cats.syntax.show
import com.teletracker.common.elasticsearch.EsItemReleaseDate
import com.teletracker.common.model.tmdb.MovieCountryRelease
import com.teletracker.tasks.TeletrackerTaskWithDefaultArgs
import com.twitter.util.StorageUnit
import javax.inject.Inject
import java.net.URI
import com.teletracker.common.util.Lists._
import java.time.OffsetDateTime
import scala.io.Source
import io.circe.syntax._
import scala.util.Try

class ImportReleaseDatesElasticsearch @Inject()()
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val thingInput = args.value[URI]("thingMapping").get
    val offset = args.valueOrDefault[Int]("offset", 0)
    val limit = args.valueOrDefault("limit", -1)

    val fileRotator =
      new FileRotator("release_dates", StorageUnit.fromMegabytes(99), None)

    val src = Source.fromURI(thingInput)
    try {
      src
        .getLines()
        .zipWithIndex
        .drop(offset)
        .safeTake(limit)
        .foreach {
          case (line, idx) => {
            SqlDumpSanitizer
              .extractThingFromLine(line, Some(idx))
              .foreach(thing => {
                thing.metadata
                  .flatMap(_.tmdbMovie)
                  .foreach(movie => {
                    val dates = movie.release_dates.map(_.results.map(mrd => {
                      val earliest = findEarliestReleaseDate(mrd.release_dates)
                      EsItemReleaseDate(
                        mrd.iso_3166_1,
                        earliest.map(_._1.toLocalDate),
                        earliest.flatMap(_._2.certification)
                      )
                    }))

                    fileRotator.writeLines(
                      Seq(
                        Map(
                          "update" -> Map(
                            "_id" -> thing.id.toString,
                            "_index" -> "items"
                          )
                        ).asJson.noSpaces,
                        Map(
                          "doc" -> Map("release_dates" -> dates.asJson)
                        ).asJson.noSpaces
                      )
                    )
                  })

                thing.metadata
                  .flatMap(_.tmdbShow)
                  .foreach(show => {
                    val dates =
                      show.content_ratings
                        .map(_.results.map(rating => {
                          EsItemReleaseDate(
                            rating.iso_3166_1,
                            None,
                            Some(rating.rating)
                          )
                        }))
                        .getOrElse(Nil)

                    fileRotator.writeLines(
                      Seq(
                        Map(
                          "update" -> Map(
                            "_id" -> thing.id.toString,
                            "_index" -> "items"
                          )
                        ).asJson.noSpaces,
                        Map(
                          "doc" -> Map("release_dates" -> dates.asJson)
                        ).asJson.noSpaces
                      )
                    )
                  })
              })
          }
        }
    } finally {
      src.close()
    }

    fileRotator.finish()
  }

  private def findEarliestReleaseDate(releases: List[MovieCountryRelease]) = {
    releases
      .flatMap(release => {
        release.release_date
          .flatMap(rd => Try(OffsetDateTime.parse(rd)).toOption)
          .map(dt => dt -> release)
      })
      .sortWith {
        case ((dt1, _), (dt2, _)) => dt1.isBefore(dt2)
      }
      .headOption
  }
}
