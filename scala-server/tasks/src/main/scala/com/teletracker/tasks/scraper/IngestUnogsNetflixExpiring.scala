package com.teletracker.tasks.scraper

import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client

class IngestUnogsNetflixExpiring @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[UnogsScrapeItem] {
  override protected def networkNames: Set[String] = Set("netflix")

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode = elasticsearchLookup
}

@JsonCodec
case class UnogsScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  status: String,
  `type`: ThingType,
  externalId: Option[String])
    extends ScrapedItem {
  override def category: String = ""

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show
}
