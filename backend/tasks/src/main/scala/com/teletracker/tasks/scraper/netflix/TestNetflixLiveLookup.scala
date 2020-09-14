package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.http.{HttpClient, HttpClientOptions}
import com.teletracker.common.tasks.TypedTeletrackerTask
import com.teletracker.common.tasks.args.GenArgParser
import com.teletracker.common.util.Futures._
import io.circe.generic.JsonCodec
import javax.inject.Inject
import scala.concurrent.ExecutionContext

@GenArgParser
@JsonCodec
case class TestNetflixLiveLookupArgs(id: String)

object TestNetflixLiveLookupArgs

class TestNetflixLiveLookup @Inject()(
  httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext)
    extends TypedTeletrackerTask[TestNetflixLiveLookupArgs] {
  final private val netflixClient =
    httpClientFactory.create("www.netflix.com", HttpClientOptions.withTls)

  override protected def runInternal(): Unit = {
    netflixClient
      .get(s"/title/${args.id}")
      .map(_.status)
      .map {
        case 404 =>
          println(s"${args.id} got 404")
        case _ =>
      }
      .await()
  }
}
