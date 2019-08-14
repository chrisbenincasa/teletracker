package com.teletracker.tasks

import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.model.tmdb.{PagedResult, Person}
import com.teletracker.common.process.tmdb.{ItemExpander, TmdbEntityProcessor}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.json.circe._
import javax.inject.Inject
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImportPeople @Inject()(
  tmdbClient: TmdbClient,
  processor: TmdbEntityProcessor,
  expander: ItemExpander)
    extends TeletrackerTask {
  override def run(args: Args): Unit = {
    val endpoint = args.valueOrDefault[String]("endpoint", "popular")
    val pages = args.valueOrDefault[Int]("pages", 5)

    val requests = (1 to pages).toList.map(
      i =>
        () =>
          tmdbClient.makeRequest[PagedResult[Person]](
            s"/person/$endpoint",
            Seq("page" -> i.toString)
          )
    )

    val processed = SequentialFutures.serialize(requests)(r => {
      r().flatMap(res => {
        SequentialFutures.serialize(res.results, Some(250 millis))(person => {
          expander
            .expandPerson(person.id.toString)
            .flatMap(processor.handlePerson)
        })
      })
    })

    Await.result(processed, Duration.Inf)
  }
}
