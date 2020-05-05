package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.{IngestJob, IngestJobParser, ScrapedItem}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client

class IngestUnogsNetflixExpiring @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater)
    extends IngestJob[UnogsScrapeItem] {

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Netflix)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine
}

@JsonCodec
case class UnogsScrapeItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  status: String,
  `type`: ItemType,
  externalId: Option[String])
    extends ScrapedItem {
  override def category: Option[String] = None

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override def description: Option[String] = None
}
