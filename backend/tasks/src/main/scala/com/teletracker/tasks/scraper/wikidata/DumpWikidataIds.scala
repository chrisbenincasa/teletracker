package com.teletracker.tasks.scraper.wikidata

import com.teletracker.common.db.model.ExternalSource
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.tasks.TeletrackerTaskWithDefaultArgs
import com.teletracker.common.util.AsyncStream
import com.teletracker.tasks.model.EsItemDumpRow
import com.teletracker.common.util.Futures._
import com.teletracker.tasks.scraper.IngestJobParser
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import javax.inject.Inject
import java.io.{BufferedOutputStream, File, FileOutputStream, PrintWriter}
import java.net.{URI, URLEncoder}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

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

  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private lazy val httpClient =
    httpClientFactory.create("query.wikidata.org", HttpClientOptions.withTls)

  override protected def runInternal(args: Args): Unit = {
    val itemDumpInput = args.valueOrThrow[URI]("itemDumpInput")
    val offset = args.valueOrDefault("offset", 0)
    val limit = args.valueOrDefault("limit", -1)
    val append = args.valueOrDefault("append", false)

    val writer = new PrintWriter(
      new BufferedOutputStream(
        new FileOutputStream(new File("imdb_to_wikidata_ids.txt"), append)
      )
    )

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
                    .filter(_.nonEmpty)
                    .filterNot(_ == "0")
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
      .drop(offset)
      .safeTake(limit)
      .grouped(50)
      .delayedMapF(500 millis, scheduler)(group => {
        httpClient
          .getJson(
            HttpRequest(
              "/sparql",
              params = Map(
                "query" -> URLEncoder
                  .encode(imdbQuery(IMDB_ID_PROP, group.toSet), "utf-8"),
                "format" -> "json"
              ).toList,
              headers = Map(
                "User-Agent" -> USER_AGENT_STRING,
                "Accept" -> "application/sparql-results+json"
              ).toList
            )
          )
          .map(response => {
            response.content.as[WikidataSpaqlResult] match {
              case Left(value) =>
                logger.error("Error", value)
              case Right(value) =>
                value.results.bindings
                  .map(
                    binding =>
                      List(
                        binding.imdb_id.value,
                        binding.item.value
                          .split('/')
                          .last
                      ).mkString(",")
                  )
                  .foreach(writer.println)
            }
          })
      })
      .force
      .await()

    writer.flush()
    writer.close()
  }
}

@JsonCodec
case class WikidataSpaqlResult(results: WikidataSpaqlResults)

@JsonCodec
case class WikidataSpaqlResults(bindings: List[WikidataSpaqlBinding])

@JsonCodec
case class WikidataSpaqlBinding(
  imdb_id: WikidataSpaqlTypeBinding,
  item: WikidataSpaqlTypeBinding)

@JsonCodec
case class WikidataSpaqlTypeBinding(
  `type`: String,
  value: String)
