package com.teletracker.service.tools

import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.Modules
import com.teletracker.service.model.tmdb.{Movie, PagedResult}
import com.teletracker.service.util.TmdbMovieImporter
import com.teletracker.service.util.execution.SequentialFutures
import com.google.inject.Module
import com.twitter.inject.app.App
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object ImportMovies extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val tmdbClient = injector.instance[TmdbClient]
    val importer = injector.instance[TmdbMovieImporter]

    val endpoint = args.headOption.getOrElse("popular")
    val pages = args.drop(1).headOption.map(_.toInt).getOrElse(5)

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
        importer.handleMovies(res.results)
      })
    })

    Await.result(processed, Duration.Inf)
  }
}
