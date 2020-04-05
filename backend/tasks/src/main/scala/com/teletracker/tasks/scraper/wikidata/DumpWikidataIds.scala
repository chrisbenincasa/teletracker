package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.{URI, URLEncoder}
import scala.concurrent.{ExecutionContext, Future}

object DumpWikidataIds {
  final val USER_AGENT_STRING =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"
  final val IMDB_ID_PROP = "P345"

  def imdbQuery(
    property: String,
    ids: Set[String]
  ): String =
    s"""
      |SELECT ?item ?imdb_id WHERE {
      |  ?item wdt:${property} ?imdb_id;
      |  VALUES ?imdb_id { ${ids.map(id => s"""\"$id\"""").mkString(" ")} }
      |}
      |""".stripMargin
}

class DumpWikidataIds @Inject()(
  sourceRetriever: SourceRetriever,
  httpClientFactory: HttpClient.Factory
)(implicit executionContext: ExecutionContext)
    extends TeletrackerTaskWithDefaultArgs {
  import DumpWikidataIds._

  private lazy val httpClient =
    httpClientFactory.create("query.wikidata.org", HttpClientOptions.withTls)

  override protected def runInternal(args: Args): Unit = {
    val itemDumpInput = args.valueOrThrow[URI]("itemDumpInput")

    val parser = new IngestJobParser
    val imdbIds = sourceRetriever
      .getSourceAsyncStream(itemDumpInput)
      .mapConcurrent(16)(source => {
        Future {
          try {
            parser
              .stream[EsItemDumpRow](source.getLines())
              .collect {
                case Right(value) =>
                  value._source.externalIdsGrouped
                    .get(ExternalSource.Imdb)
              }
              .flatten
              .toList
          } finally {
            source.close()
          }
        }
      })
      .foldLeft(Set.empty[String])(_ ++ _)
      .await()

    println(s"Collected ${imdbIds.size} ids")

    AsyncStream
      .fromSeq(imdbIds.toSeq)
      .drop(0)
      .safeTake(50)
      .grouped(100)
      .map(_.toSet)
      .foreachF(group => {

        httpClient
          .getJson(
            HttpRequest(
              "/sparql",
              params = Map(
                "query" -> URLEncoder
                  .encode(imdbQuery(IMDB_ID_PROP, group), "utf-8"),
                "format" -> "json"
              ).toList,
              headers = Map(
                "User-Agent" -> USER_AGENT_STRING,
                "Accept" -> "application/sparql-results+json"
              ).toList
            )
          )
          .map(response => {
            response.content.asObject
              .flatMap(_.apply("results").flatMap(_.asObject))
              .flatMap(_.apply("bindings"))
              .foreach(println)
          })
      })
      .await()
  }
}
