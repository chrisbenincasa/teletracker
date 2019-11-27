package com.teletracker.tasks.scraper

import com.teletracker.common.crypto.BerglasDecoder
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.{NetworkCache, Slug}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset}
import java.util.zip.GZIPInputStream
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

class IngestHuluChanges @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  httpClient: HttpClient.Factory,
  berglasDecoder: BerglasDecoder,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[HuluScrapeItem]
    with IngestJobWithElasticsearch[HuluScrapeItem] {

  private val premiumNetworks = Set("hbo", "starz", "showtime")

  private lazy val client =
    httpClient.create("discover.hulu.com", HttpClientOptions.withTls)

  private val params = List(
    "language" -> "en",
    "schema" -> "9"
  )

  private val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36"

  private lazy val cookie =
    berglasDecoder.resolve("hulu-cookie")

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Pacific").getRules.getOffset(Instant.now())

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode =
    elasticsearchLookup

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
          .getBytes(request)
          .map(response => {
            val content =
              if (response.headers
                    .get("Content-Encoding")
                    .exists(_.contains("gzip"))) {
                Source
                  .fromInputStream(
                    new GZIPInputStream(
                      new ByteArrayInputStream(response.content)
                    )
                  )
                  .getLines()
                  .mkString("\n")
              } else {
                new String(response.content)
              }

            decode[HuluSearchResponse](content) match {
              case Left(err) =>
                logger.error(
                  s"Error while paring json response \nRequest: (${request.path}, ${request.params})\nResponse: ${response.headers}\nRaw response: ${content}",
                  err
                )

                None
              case Right(searchResult) =>
                val allResults = searchResult.groups
                  .flatMap(_.results)
                  .flatMap(_.entity_metadata)

                allResults.headOption
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

          lookupByNames(missingItemByTitle)
            .map(foundThingsByTitle => {
              foundThingsByTitle.toList
            })
            .map(foundThings.toList ++ _)
            .map(_.map {
              case (amended, thingRaw) =>
                val originalItem = originalByAmends(amended)
                logger.info(
                  s"Successfully found fallback match for ${thingRaw.title.get.head} (${thingRaw.id} (Original item title: ${originalItem.title}))"
                )
                NonMatchResult(
                  amended,
                  originalItem,
                  thingRaw.id,
                  thingRaw.title.get.head
                )
            })
        })
      })
  }

  private def lookupByNames(itemsByTitle: Map[String, HuluScrapeItem]) = {
    val titleTriples = itemsByTitle.map {
      case (title, item) =>
        (
          title,
          item.thingType,
          item.releaseYear.map(ry => (ry - 1) to (ry + 1))
        )
    }.toList

    itemSearch
      .lookupItemsByTitleMatch(titleTriples)
      .map(matchesByTitle => {
        matchesByTitle.collect {
          case (title, esItem)
              if title.equalsIgnoreCase(
                esItem.original_title.getOrElse("")
              ) || title.equalsIgnoreCase(esItem.title.get.head) =>
            itemsByTitle
              .get(title)
              .map(_ -> esItem)
        }.flatten
      })
  }

  private def lookupBySlugs(itemBySlug: Map[Slug, HuluScrapeItem]) = {
    itemSearch
      .lookupItemsBySlug(
        itemBySlug
          .filter(_._2.thingType.isDefined)
          .map {
            case (slug, item) =>
              (
                slug,
                item.thingType.get,
                item.releaseYear.map(ry => (ry - 1) to (ry + 1))
              )
          }
          .toList
      )
      .map(foundBySlug => {
        foundBySlug.flatMap {
          case (slug, esItem) =>
            itemBySlug.get(slug).map(_ -> esItem)
        }
      })
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
