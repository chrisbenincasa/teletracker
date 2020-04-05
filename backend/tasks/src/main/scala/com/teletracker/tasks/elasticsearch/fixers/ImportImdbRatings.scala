package com.teletracker.tasks.elasticsearch.fixers

import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.EsItemRating
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.Ratings
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import javax.inject.Inject
import java.net.URI
import io.circe.syntax._
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ImportImdbRatings @Inject()(
  sourceRetriever: SourceRetriever,
  teletrackerConfig: TeletrackerConfig
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  override protected def runInternal(args: Args): Unit = {
    val itemDumpInput = args.valueOrThrow[URI]("itemDumpInput")
    val imdbImport = args.valueOrThrow[URI]("imdbInput")

    val tmdbMovieWeight = 100
    val tmdbShowWeight = 25
    val imdbWeight = 5000

    val rotater = FileRotator.everyNLines(
      "rating_updates",
      20000,
      Some("rating_updates")
    )

    val parser = new IngestJobParser

    val allRanks = sourceRetriever
      .getSourceStream(imdbImport)
      .foldLeft(Map.empty[String, (Float, Int)]) {
        case (acc, source) =>
          try {
            acc ++ source
              .getLines()
              .drop(1)
              .toStream
              .map(line => {
                line.split("\t") match {
                  case Array(id, avgRating, numVotes) =>
                    id -> (avgRating.toFloat, numVotes.toInt)
                }
              })
          } finally {
            source.close()
          }
      }

    val (imdbIdById, movieRatingById, showRatingById) = sourceRetriever
      .getSourceAsyncStream(itemDumpInput)
      .mapConcurrent(8)(source => {
        Future {
          try {
            parser
              .stream[EsItemDumpRow](source.getLines())
              .collect {
                case Right(value)
                    if value._source.externalIdsGrouped
                      .contains(ExternalSource.Imdb) =>
                  value._source
              }
              .foldLeft(
                (
                  Map.empty[UUID, String],
                  Map.empty[UUID, (Double, Int)],
                  Map.empty[UUID, (Double, Int)]
                )
              ) {
                case ((imdbToIdAcc, movieRatingAcc, showRatingAcc), item) =>
                  val map1 = imdbToIdAcc.updated(
                    item.id,
                    item.externalIdsGrouped(ExternalSource.Imdb)
                  )
                  if (item.`type` == ItemType.Movie) {
                    val map2 = item.ratingsGrouped
                      .get(ExternalSource.TheMovieDb)
                      .flatMap(rating => {
                        rating.vote_count.map(
                          count =>
                            movieRatingAcc
                              .updated(item.id, rating.vote_average -> count)
                        )

                      })
                      .getOrElse(movieRatingAcc)

                    (map1, map2, showRatingAcc)
                  } else {
                    val map2 = item.ratingsGrouped
                      .get(ExternalSource.TheMovieDb)
                      .flatMap(rating => {
                        rating.vote_count.map(
                          count =>
                            showRatingAcc
                              .updated(item.id, rating.vote_average -> count)
                        )

                      })
                      .getOrElse(showRatingAcc)

                    (map1, movieRatingAcc, map2)
                  }
              }
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(
        (
          Map.empty[UUID, String],
          Map.empty[UUID, (Double, Int)],
          Map.empty[UUID, (Double, Int)]
        )
      ) {
        case ((lm1, mm1, rm1), (lm2, mm2, rm2)) =>
          ((lm1 ++ lm2), (mm1 ++ mm2), (rm1 ++ rm2))
      }
      .await()

    val imdbAverageRating = allRanks.values.map(_._1).sum / allRanks.size

    val tmdbMovieAverageRating = movieRatingById.values
      .map(_._1)
      .sum / movieRatingById.size
    val tmdbShowAverageRating = showRatingById.values
      .map(_._1)
      .sum / showRatingById.size

    val generatedAt = Instant.now()

    (movieRatingById.keySet
      .union(showRatingById.keySet))
      .foreach(id => {
        val imdbId = imdbIdById(id)

        val tmdbRating = if (movieRatingById.contains(id)) {
          val (tmdbAvgRating, tmdbVotes) = movieRatingById(id)

          val tmdbWeighted = Ratings
            .weightedAverage(
              tmdbAvgRating,
              tmdbVotes,
              tmdbMovieAverageRating,
              tmdbMovieWeight
            )

          EsItemRating(
            provider_id = ExternalSource.TheMovieDb.ordinal(),
            provider_shortname = ExternalSource.TheMovieDb.toString,
            vote_average = tmdbAvgRating,
            vote_count = Some(tmdbVotes),
            weighted_average = Some(tmdbWeighted),
            weighted_last_generated = Some(generatedAt)
          )
        } else {
          val (tmdbAvgRating, tmdbVotes) = showRatingById(id)

          val tmdbWeighted = Ratings
            .weightedAverage(
              tmdbAvgRating,
              tmdbVotes,
              tmdbShowAverageRating,
              tmdbShowWeight
            )

          EsItemRating(
            provider_id = ExternalSource.TheMovieDb.ordinal(),
            provider_shortname = ExternalSource.TheMovieDb.toString,
            vote_average = tmdbAvgRating,
            vote_count = Some(tmdbVotes),
            weighted_average = Some(tmdbWeighted),
            weighted_last_generated = Some(generatedAt)
          )
        }

        val imdbRating = allRanks
          .get(imdbId)
          .map {
            case (itemAvgRating, imdbNumVotes) =>
              val imdbWeighted = Ratings.weightedAverage(
                itemAvgRating,
                imdbNumVotes,
                imdbAverageRating,
                imdbWeight
              )

              EsItemRating(
                provider_id = ExternalSource.Imdb.ordinal(),
                provider_shortname = ExternalSource.Imdb.toString,
                vote_average = itemAvgRating,
                vote_count = Some(imdbNumVotes),
                weighted_average = Some(imdbWeighted),
                weighted_last_generated = Some(generatedAt)
              )
          }
          .toList

        val allRatings = List(tmdbRating) ++ imdbRating

        val newRatings =
          Map("doc" -> Map("ratings" -> allRatings.asJson).asJson).asJson
        val update = EsBulkUpdate(
          index = teletrackerConfig.elasticsearch.items_index_name,
          id,
          newRatings.noSpaces
        )

        rotater.writeLines(update.lines)
      })
  }
}
