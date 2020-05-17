package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.util.{FileRotator, SourceRetriever}
import com.twitter.util.StorageUnit
import javax.inject.Inject
import io.circe.syntax._
import java.net.{URI, URLEncoder}
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

class DumpWikidataForIds @Inject()(
  sourceRetriever: SourceRetriever,
  httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  final val USER_AGENT_STRING =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"

  private lazy val httpClient =
    httpClientFactory.create("www.wikidata.org", HttpClientOptions.withTls)

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  override protected def runInternal(args: Args): Unit = {
    val input = args.valueOrThrow[URI]("input")

    val rotator = FileRotator.everyNBytes(
      "wikidata_dump",
      StorageUnit.fromMegabytes(50),
      Some("wikidata_dump")
    )

    sourceRetriever
      .getSourceStream(input)
      .foreach(source => {
        try {
          AsyncStream
            .fromStream(source.getLines().toStream)
            .flatMapOption(
              line =>
                Try {
                  val Array(imdbId, wikiId) = line.split(",", 2)
                  imdbId -> wikiId
                }.toOption
            )
            .grouped(50)
            .delayedMapF(1 second, scheduler)(batch => {
              val wikiIds = batch.map(_._2).mkString("|")
              httpClient
                .getJson(
                  HttpRequest(
                    "/w/api.php",
                    params = Map(
                      "action" -> "wbgetentities",
                      "ids" -> URLEncoder.encode(wikiIds, "utf-8"),
                      "format" -> "json"
                    ).toList,
                    headers = Map(
                      "User-Agent" -> USER_AGENT_STRING
                    ).toList
                  )
                )
                .map(response => {
                  val lines = batch.flatMap {
                    case (imdbId, wikiId) =>
                      response.content.asObject
                        .flatMap(_.apply("entities"))
                        .flatMap(_.asObject)
                        .flatMap(_.apply(wikiId))
                        .map(
                          json =>
                            Map("imdbId" -> imdbId.asJson, "entity" -> json).asJson.noSpaces
                        )
                  }

                  rotator.writeLines(lines)
                })
            })
            .force
            .await()
        } finally {
          source.close()
        }
      })

    rotator.finish()
  }
}
