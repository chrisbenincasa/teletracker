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
  ): Future[List[NonMatchResult]] = {
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
                Nil
              case Right(searchResult) =>
                searchResult.groups
                  .flatMap(_.results)
                  .flatMap(_.entity_metadata)
                  .map(meta => {
                    val amended = nonMatch.copy(
                      releaseYear = meta.premiere_date
                        .map(OffsetDateTime.parse(_))
                        .map(_.getYear),
                      title = sanitizeTitle(meta.target_name)
                    )

                    nonMatch -> amended
                  })
            }
          })
      })
      .map(_.flatten)
      .flatMap(nonMatchesAndAmends => {
        val amendsByOriginal =
          nonMatchesAndAmends.groupBy(_._1).mapValues(_.map(_._2))

        val originalByAmends = nonMatchesAndAmends
          .map(_.swap)
          .map { case (amend, original) => amend -> original }
          .toMap

        val amendedItemsBySlugs = amendsByOriginal.values.flatten
          .flatMap(amendedItem => {
            amendedItem.releaseYear
              .map(Slug(amendedItem.title, _))
              .map(_ -> amendedItem)
          })
          .toMap

        lookupBySlugs(amendedItemsBySlugs).flatMap(foundThings => {
          val foundTitles = foundThings.map(_._1.title).toSet
          val missingScrapedItems =
            nonMatchesAndAmends
              .map(_._2)
              .filterNot(item => foundTitles.contains(item.title))

          val missingItemByTitle =
            missingScrapedItems.map(item => item.title -> item).toMap

          thingsDb
            .findThingsByNames(missingItemByTitle.keySet)
            .map(foundThingsByTitle => {
              foundThingsByTitle.toList.flatMap {
                case (title, things) if things.length == 1 =>
                  missingItemByTitle
                    .filterKeys(_.toLowerCase == title)
                    .headOption
                    .map {
                      case (_, item) => item -> things.head
                    }

                case (title, things) =>
                  logger.info(
                    s"Couldn't match on name: ${things.length} things on name match with ${title}"
                  )

                  None
              }
            })
            .map(foundThings.toList ++ _)
            .map(_.map {
              case (amended, thingRaw) =>
                NonMatchResult(amended, originalByAmends(amended), thingRaw)
            })
        })
      })
  }

  private def lookupBySlugs(itemBySlug: Map[Slug, HuluScrapeItem]) = {
    Future
      .sequence(
        itemBySlug.keys
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
  }

  private val endsWithNote = "\\([A-z0-9]+\\)$".r

  override protected def sanitizeItem(item: HuluScrapeItem): HuluScrapeItem =
    item.copy(
      title = sanitizeTitle(item.title)
    )

  private def sanitizeTitle(title: String): String = {
    endsWithNote.replaceAllIn(title, "")
  }
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
