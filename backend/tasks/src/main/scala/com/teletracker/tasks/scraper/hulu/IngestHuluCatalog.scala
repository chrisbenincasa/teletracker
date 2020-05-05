package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.util.json.circe._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.{NetworkCache, Slug}
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatching,
  ElasticsearchLookup,
  LookupMethod
}
import com.teletracker.tasks.scraper.{IngestJob, IngestJobParser, ScrapedItem}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}

class IngestHuluCatalog @Inject()(
  protected val teletrackerConfig: TeletrackerConfig,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HuluCatalogItem]
    with ElasticsearchFallbackMatching[HuluCatalogItem] {

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Hulu)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def shouldProcessItem(item: HuluCatalogItem): Boolean = {
    !item.title.toLowerCase().contains("en español") &&
    !item.title.toLowerCase().contains("en espanol")
  }

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

  override protected def itemUniqueIdentifier(item: HuluCatalogItem): String =
    item.externalId.get

  override protected def getExternalIds(
    item: HuluCatalogItem
  ): Map[ExternalSource, String] =
    item.externalId
      .map(id => Map(ExternalSource.Hulu -> id))
      .getOrElse(Map.empty)
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
  genres: Option[List[String]],
  description: Option[String])
    extends ScrapedItem {
  override def category: Option[String] = None

  override def status: String = ""

  override def availableDate: Option[String] = availableOn

  override def title: String = name

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)

  override def url: Option[String] = {
    externalId.map(eid => {
      s"https://www.hulu.com/${makeHuluType(`type`)}/${makeHuluSlug(title, eid)}"
    })
  }

  private def makeHuluType(thingType: ItemType) = {
    thingType match {
      case ItemType.Movie  => "movie"
      case ItemType.Show   => "series"
      case ItemType.Person => throw new IllegalArgumentException
    }
  }

  private def makeHuluSlug(
    title: String,
    id: String
  ) = {
    Slug.apply(title, None).addSuffix(id).toString
  }
}
