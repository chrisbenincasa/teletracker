package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.model.ItemType
import com.teletracker.common.util.json.circe._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatching,
  ElasticsearchLookup,
  MatchMode
}
import com.teletracker.tasks.scraper.{IngestJob, IngestJobParser, ScrapedItem}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}

class IngestHuluCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[HuluCatalogItem]
    with ElasticsearchFallbackMatching[HuluCatalogItem] {

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def matchMode: MatchMode =
    elasticsearchLookup

  override protected def sanitizeItem(item: HuluCatalogItem): HuluCatalogItem =
    if (item.releaseYear.isDefined && item.name.endsWith(
          s"(${item.releaseYear.get})"
        )) {
      item.copy(
        name =
          item.name.replaceAllLiterally(s"(${item.releaseYear.get})", "").trim
      )
    } else {
      item
    }

  override protected def isAvailable(
    item: HuluCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }
}

@JsonCodec
case class HuluCatalogItem(
  availableOn: Option[String],
  expiresOn: Option[String],
  name: String,
  releaseYear: Option[Int],
  network: String,
  `type`: ItemType,
  externalId: Option[String],
  numSeasonsAvailable: Option[Int],
  genres: Option[List[String]])
    extends ScrapedItem {
  override def category: String = ""

  override def status: String = ""

  override def availableDate: Option[String] = availableOn

  override def title: String = name

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
}
