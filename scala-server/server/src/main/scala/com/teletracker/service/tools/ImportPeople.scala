package com.teletracker.service.tools

import com.teletracker.service.external.tmdb.TmdbClient
import com.teletracker.service.inject.Modules
import com.teletracker.service.model.tmdb.{PagedResult, Person, TvShow}
import com.teletracker.service.process.tmdb.{ItemExpander, TmdbEntityProcessor}
import com.teletracker.service.util.execution.SequentialFutures
import com.teletracker.service.util.json.circe._
import com.google.inject.Module
import com.twitter.inject.app.App
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ImportPeople extends App {
  override protected def modules: Seq[Module] = Modules()

  override protected def run(): Unit = {
    val tmdbClient = injector.instance[TmdbClient]
    val processor = injector.instance[TmdbEntityProcessor]
    val expander = injector.instance[ItemExpander]

    val endpoint = args.headOption.getOrElse("popular")
    val pages = args.drop(1).headOption.map(_.toInt).getOrElse(5)

    val requests = (1 to pages).toList.
      map(i => () => tmdbClient.makeRequest[PagedResult[Person]](s"/person/$endpoint", Seq("page" -> i.toString)))

    val processed = SequentialFutures.serialize(requests)(r => {
      r().flatMap(res => {
        SequentialFutures.serialize(res.results, Some(250 millis))(person => {
          expander.expandPerson(person.id.toString).flatMap(processor.handlePerson)
        })
      })
    })

    Await.result(processed, Duration.Inf)
  }
}
