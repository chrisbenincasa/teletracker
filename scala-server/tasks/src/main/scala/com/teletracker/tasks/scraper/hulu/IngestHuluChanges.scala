package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.crypto.BerglasDecoder
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.http.HttpClient
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import com.teletracker.tasks.scraper.model.NonMatchResult
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, ZoneId, ZoneOffset}
import scala.concurrent.Future

class IngestHuluChanges @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  httpClient: HttpClient.Factory,
  berglasDecoder: BerglasDecoder,
  elasticsearchLookup: ElasticsearchLookup,
  huluFallbackMatching: HuluFallbackMatching)
    extends IngestJob[HuluScrapeItem] {

  private val premiumNetworks = Set("hbo", "starz", "showtime")

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
    huluFallbackMatching.handleNonMatches(args, nonMatches)
  }

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
      title = HuluSanitization.sanitizeTitle(item.title)
    )
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

@JsonCodec
case class HuluSearchResponse(groups: List[HuluSearchResponseGroup])

@JsonCodec
case class HuluSearchResponseGroup(results: List[HuluSearchResponseResult])

@JsonCodec
case class HuluSearchResponseResult(
  entity_metadata: Option[HuluSearchResultMetadata],
  metrics_info: Option[HuluSearchMetricsInfo])

@JsonCodec
case class HuluSearchResultMetadata(
  premiere_date: Option[String],
  target_name: String)

@JsonCodec
case class HuluSearchMetricsInfo(target_type: String)
