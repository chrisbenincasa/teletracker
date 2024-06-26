package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.crypto.SecretResolver
import com.teletracker.common.elasticsearch.model.EsItem
import com.teletracker.common.elasticsearch.{FuzzyItemLookupRequest, ItemLookup}
import com.teletracker.common.http.{HttpClient, HttpClientOptions, HttpRequest}
import com.teletracker.common.model.scraping.NonMatchResult
import com.teletracker.common.model.scraping.hulu.{
  HuluScrapeItem,
  HuluSearchResponse
}
import com.teletracker.common.util.{RelativeRange, Slug}
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.scraper.{model, IngestJobArgs}
import io.circe.parser.decode
import javax.inject.Inject
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class HuluFallbackMatching @Inject()(
  itemLookup: ItemLookup,
  httpClient: HttpClient.Factory,
  secretResolver: SecretResolver
)(implicit executionContext: ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val client =
    httpClient.create("discover.hulu.com", HttpClientOptions.withTls)

  private val params = List(
    "language" -> "en",
    "schema" -> "9"
  )

  private val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36"

  private lazy val cookie =
    secretResolver.resolve("hulu-cookie")

  def handleNonMatches(
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
                      title = HuluSanitization.sanitizeTitle(meta.target_name)
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
                  thingRaw
                )
            })
        })
      })
  }

  // TODO: Should this use the ES fallback matcher?
  private def lookupByNames(itemsByTitle: Map[String, HuluScrapeItem]) = {
    val requestsAndItems = itemsByTitle.map {
      case (title, item) =>
        FuzzyItemLookupRequest(
          title = title,
          description = item.description,
          itemType = Some(item.itemType),
          exactReleaseYear = item.releaseYear.map(_ -> 3.0f),
          releaseYearTiers = Some(Seq(RelativeRange.forInt(1) -> 1.0f)),
          looseReleaseYearMatching = false,
          popularityThreshold = Some(1.0),
          castNames = item.cast.map(_.map(_.name).toSet),
          crewNames = item.crew.map(_.map(_.name).toSet),
          strictTitleMatch = true,
          limit = 1
        ) -> item
    }.toList

    val itemByRequestId = requestsAndItems.map {
      case (request, item) => request.id -> item
    }.toMap

    val requestsById =
      requestsAndItems.map(_._1).map(req => req.id -> req).toMap

    itemLookup
      .lookupFuzzyBatch(requestsAndItems.map(_._1))
      .map(matchesByRequestId => {
        itemByRequestId.foldLeft(Map.empty[HuluScrapeItem, EsItem]) {
          case (acc, (requestId, scrapeItem)) =>
            val titleFromRequest = requestsById(requestId).title
            val matchPair = matchesByRequestId.get(requestId) match {
              case Some(response) =>
                response.items
                  .find(item => {
                    val titleMatch =
                      item.title.get.exists(titleFromRequest.equalsIgnoreCase)
                    val altTitleMatch = item.alternative_titles
                      .getOrElse(Nil)
                      .filter(_.country_code == "US")
                      .exists(_.title.equalsIgnoreCase(titleFromRequest))
                    titleMatch || altTitleMatch
                  })
                  .map(scrapeItem -> _)
              case None => None
            }

            matchPair.map(acc + _).getOrElse(acc)
        }
      })
  }

  private def lookupBySlugs(itemBySlug: Map[Slug, HuluScrapeItem]) = {
    itemLookup
      .lookupItemsBySlug(
        itemBySlug.map {
          case (slug, item) =>
            (
              slug,
              item.itemType,
              item.releaseYear.map(ry => (ry - 1) to (ry + 1))
            )
        }.toList
      )
      .map(foundBySlug => {
        foundBySlug.flatMap {
          case (slug, esItem) =>
            itemBySlug.get(slug).map(_ -> esItem)
        }
      })
  }
}
