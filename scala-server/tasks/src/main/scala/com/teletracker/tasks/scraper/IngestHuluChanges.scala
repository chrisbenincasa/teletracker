package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.crypto.BerglasDecoder
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.ThingRaw
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.http.{
  BlockingHttp,
  HttpClient,
  HttpClientOptions,
  HttpRequest
}
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.{NetworkCache, Slug}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.json.circe._
import io.circe.generic.auto._
import io.circe._
import io.circe.parser._
import javax.inject.Inject
import java.net.URLEncoder
import java.nio.charset.Charset
import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class IngestHuluChanges @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val storage: Storage,
  protected val networkCache: NetworkCache,
  httpClient: HttpClient.Factory,
  berglasDecoder: BerglasDecoder)
    extends IngestJob[HuluScrapeItem] {
  private lazy val client =
    httpClient.create("discover.hulu.com", HttpClientOptions.withTls)

  private val params = List(
    "language" -> "en"
  )

  private val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36"

  private lazy val cookie =
    berglasDecoder.resolve("teletracker-secrets/hulu-cookie")

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[HuluScrapeItem]
  ): Future[List[(HuluScrapeItem, ThingRaw)]] = {
    SequentialFutures
      .serialize(nonMatches, Some(250.millis))(nonMatch => {
        val request = HttpRequest(
          "/content/v4/search/entity",
          params :+ ("search_query" -> URLEncoder
            .encode(nonMatch.title, "UTF-8")),
          List(
            "User-Agent" -> userAgent,
            "Cookie" -> cookie
          )
        )

        client
          .get(request)
          .map(response => {
            decode[HuluSearchResponse](response.content) match {
              case Left(err) =>
                logger.error("Error while paring json response", err)
                None
              case Right(searchResult) =>
                searchResult.groups
                  .flatMap(_.results)
                  .headOption
                  .flatMap(_.entity_metadata)
                  .flatMap(meta => {
                    meta.premiere_date
                      .map(OffsetDateTime.parse(_))
                      .map(_.getYear)
                      .map(year => {
                        Slug(meta.target_name, year) -> nonMatch
                      })
                  })
            }
          })
      })
      .map(_.flatten)
      .flatMap(slugsAndItems => {
        val itemBySlug = slugsAndItems.toMap
        Future
          .sequence(
            slugsAndItems
              .map(_._1)
              .map(slug => {
                thingsDb
                  .findThingBySlugRaw(slug, None)
                  .map {
                    case Some(t) =>
                      logger.info(
                        s"Successfully found fallback match for ${t.name} (${t.id})"
                      )
                      Some(itemBySlug(slug) -> t)

                    case None =>
                      logger.info(
                        s"Could not find fallback match for ${itemBySlug(slug).title} via search"
                      )
                      None
                  }
              })
          )
          .map(_.flatten)
      })
  }

  private val endsWithNote = "\\([A-z0-9]+\\)$".r

  override protected def sanitizeItem(item: HuluScrapeItem): HuluScrapeItem =
    item.copy(
      title = endsWithNote.replaceAllIn(item.title, "")
    )
}

case class HuluScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  notes: String,
  category: String,
  network: String,
  status: String,
  externalId: Option[String])
    extends ScrapedItem {
  override def isMovie: Boolean = category.toLowerCase().trim() == "film"

  override def isTvShow: Boolean =
    !isMovie || category.toLowerCase().contains("series")
}

case class HuluSearchResponse(groups: List[HuluSearchResponseGroup])
case class HuluSearchResponseGroup(results: List[HuluSearchResponseResult])
case class HuluSearchResponseResult(
  entity_metadata: Option[HuluSearchResultMetadata],
  metrics_info: Option[HuluSearchMetricsInfo])
case class HuluSearchResultMetadata(
  premiere_date: Option[String],
  target_name: String)
case class HuluSearchMetricsInfo(target_type: String)
