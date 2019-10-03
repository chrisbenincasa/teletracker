package com.teletracker.tasks.tmdb

import com.teletracker.common.db.BaseDbProvider
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{Movie, PagedResult}
import com.teletracker.common.process.tmdb.TmdbMovieImporter
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.{TeletrackerTask, TeletrackerTaskWithDefaultArgs}
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ImportMovies @Inject()(
  tmdbClient: TmdbClient,
  movieImporter: TmdbMovieImporter
)(implicit baseDbProvider: BaseDbProvider)
    extends TeletrackerTaskWithDefaultArgs {
  override def runInternal(args: Args): Unit = {
    val endpoint = args.valueOrDefault[String]("endpoint", "popular")
    val pages = args.valueOrDefault[Int]("pages", 5)

    val requests = (1 to pages).toList.map(
      i =>
        () =>
          tmdbClient.makeRequest[PagedResult[Movie]](
            s"/movie/$endpoint",
            Seq("page" -> i.toString)
          )
    )

    val processed = SequentialFutures.serialize(requests)(r => {
      r().flatMap(res => {
        println(s"Got ${res.results.size} movies")
        movieImporter.handleMovies(res.results)
      })
    })

    Await.result(processed, Duration.Inf)
  }
}
