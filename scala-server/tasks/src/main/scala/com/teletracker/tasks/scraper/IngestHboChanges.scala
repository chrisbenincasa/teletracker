package com.teletracker.tasks.scraper

import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{Instant, ZoneId, ZoneOffset}

object IngestHboChanges extends IngestJobApp[IngestHboChanges]

object HboScrapeItem

class IngestHboChanges @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HboScrapeItem]
    with ElasticsearchFallbackMatching[HboScrapeItem] {

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def networkNames: Set[String] = Set("hbo-now", "hbo-go")

  override protected def matchMode: MatchMode = elasticsearchLookup

  override protected def networkTimeZone: ZoneOffset =
    ZoneId.of("US/Eastern").getRules.getOffset(Instant.now())
}

@JsonCodec
case class HboScrapeItem(
  availableDate: Option[String],
  title: String,
  parsedReleaseYear: Option[Int],
  category: String,
  network: String,
  status: String,
  externalId: Option[String])
    extends ScrapedItem {
  override def isMovie: Boolean = category.toLowerCase().trim() == "film"

  override def isTvShow: Boolean = false

  override def releaseYear: Option[Int] = parsedReleaseYear
}
