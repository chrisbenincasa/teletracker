package com.teletracker.tasks.tmdb

import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{PagedResult, TvShow}
import com.teletracker.common.process.tmdb.TmdbShowImporter
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImportTv @Inject()(
  tmdbClient: TmdbClient,
  importer: TmdbShowImporter)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val endpoint = args.valueOrDefault[String]("endpoint", "popular")
    val pages = args.valueOrDefault[Int]("pages", 5)

    val requests = (1 to pages).toList.map(
      i =>
        () =>
          tmdbClient.makeRequest[PagedResult[TvShow]](
            s"/tv/$endpoint",
            Seq("page" -> i.toString)
          )
    )

    val processed = SequentialFutures.serialize(requests)(r => {
      r().flatMap(res => {
        SequentialFutures.serialize(res.results, Some(250 millis))(show => {
          tmdbClient
            .makeRequest[TvShow](
              s"/tv/${show.id}",
              Seq(
                "append_to_response" -> Seq(
                  "credits",
                  "release_dates",
                  "external_ids"
                ).mkString(",")
              )
            )
            .map(_ :: Nil)
            .flatMap(importer.handleShows(_, true, true))
        })
      })
    })

    Await.result(processed, Duration.Inf)
  }
}
