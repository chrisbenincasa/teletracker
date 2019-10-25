package com.teletracker.tasks.scraper

import com.teletracker.common.crypto.BerglasDecoder
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.elasticsearch.{ItemSearch, ItemUpdater}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.{NetworkCache, Slug}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import javax.inject.Inject
import org.apache.commons.text.similarity.LevenshteinDistance
import software.amazon.awssdk.services.s3.S3Client
import java.net.URLEncoder
import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.duration._

class IngestHuluChanges @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemSearch,
  protected val itemUpdater: ItemUpdater,
  httpClient: HttpClient.Factory,
  berglasDecoder: BerglasDecoder)
    extends IngestJob[HuluScrapeItem]
    with IngestJobWithElasticsearch[HuluScrapeItem] {

  private val premiumNetworks = Set("hbo", "starz", "showtime")

  private lazy val client =
    httpClient.create("discover.hulu.com", HttpClientOptions.withTls)

  private val params = List(
    "language" -> "en"
  )

  private val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36"

  private lazy val cookie =
    berglasDecoder.resolve("hulu-cookie")

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[HuluScrapeItem]
  ): Future[List[NonMatchResult[HuluScrapeItem]]] = {
    SequentialFutures
      .serialize(nonMatches, Some(250.millis))(nonMatch => {
        logger.info("Searching hulu for " + nonMatch.title)
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
                val allResults = searchResult.groups
                  .flatMap(_.results)
                  .flatMap(_.entity_metadata)

                allResults
                  .sortBy(result => {
                    LevenshteinDistance.getDefaultInstance
                      .apply(sanitizeTitle(result.target_name), nonMatch.title)
                  })
                  .headOption
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
                val originalItem = originalByAmends(amended)
                logger.info(
                  s"Successfully found fallback match for ${thingRaw.name} (${thingRaw.id} (Original item title: ${originalItem.title}))"
                )
                NonMatchResult(
                  amended,
                  originalItem,
                  thingRaw.id,
                  thingRaw.name
                )
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
  private val badApostrophe = "[A-z]\\?[A-z]".r

  override protected def shouldProcessItem(item: HuluScrapeItem): Boolean = {
    val category = item.category.toLowerCase()
    val shouldInclude = if (premiumNetworks.exists(category.contains)) {
      if (category.contains("series")) {
        !item.notes
          .toLowerCase()
          .contains("premiere")
      } else {
        false
      }
    } else {
      true
    }

    if (!shouldInclude) {
      logger.warn(s"Not including item: ${item.title}")
    }

    shouldInclude
  }

  override protected def sanitizeItem(item: HuluScrapeItem): HuluScrapeItem =
    item.copy(
      title = sanitizeTitle(item.title)
    )

  private def sanitizeTitle(title: String): String = {
    val withoutNote = endsWithNote.replaceAllIn(title, "")
    badApostrophe
      .findAllIn(
        withoutNote
      )
      .foldLeft(withoutNote)((str, part) => {
        val replacement = part.replaceAllLiterally("?", "'")
        str.replaceAllLiterally(part, replacement)
      })
  }
}

@JsonCodec
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
