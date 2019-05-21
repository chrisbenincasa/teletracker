package com.teletracker.service.tools

import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.Modules
import com.teletracker.service.model.tmdb.{PagedResult, TvShow}
import com.teletracker.service.util.TmdbShowImporter
import com.teletracker.service.util.execution.SequentialFutures
import com.google.inject.Module
import com.twitter.inject.app.App
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ImportTv extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val tmdbClient = injector.instance[TmdbClient]
    val importer = injector.instance[TmdbShowImporter]

    val endpoint = args.headOption.getOrElse("popular")
    val pages = args.drop(1).headOption.map(_.toInt).getOrElse(5)

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
