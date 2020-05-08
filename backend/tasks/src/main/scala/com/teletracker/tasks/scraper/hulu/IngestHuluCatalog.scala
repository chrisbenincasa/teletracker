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
import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}

class IngestHuluCatalog @Inject()(
  protected val teletrackerConfig: TeletrackerConfig,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HuluScrapeCatalogItem]
    with ElasticsearchFallbackMatching[HuluScrapeCatalogItem] {

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Hulu)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def shouldProcessItem(
    item: HuluScrapeCatalogItem
  ): Boolean = {
    !item.title.toLowerCase().contains("en español") &&
    !item.title.toLowerCase().contains("en espanol") &&
    item.additionalServiceRequired.isEmpty
  }

  override protected def sanitizeItem(
    item: HuluScrapeCatalogItem
  ): HuluScrapeCatalogItem =
    if (item.releaseYear.isDefined && item.title.endsWith(
          s"(${item.releaseYear.get})"
        )) {
      item.copy(
        title =
          item.title.replaceAllLiterally(s"(${item.releaseYear.get})", "").trim
      )
    } else {
      item
    }

  override protected def isAvailable(
    item: HuluScrapeCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  override protected def itemUniqueIdentifier(
    item: HuluScrapeCatalogItem
  ): String =
    item.externalId.get

  override protected def getExternalIds(
    item: HuluScrapeCatalogItem
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
  override val numSeasonsAvailable: Option[Int],
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

@JsonCodec
case class HuluScrapeCatalogItem(
  id: String,
  availableOn: Option[String],
  expiresOn: Option[String],
  title: String,
  premiereDate: Option[String],
  network: String,
  itemType: ItemType,
  externalId: Option[String],
  genres: Option[List[String]],
  description: Option[String],
  additionalServiceRequired: Option[String],
  episodes: Option[List[HuluScrapeEpisode]])
    extends ScrapedItem {
  override def category: Option[String] = None
  override def status: String = ""
  override def availableDate: Option[String] = availableOn
  override lazy val releaseYear: Option[Int] =
    premiereDate.map(Instant.parse(_).atOffset(ZoneOffset.UTC).getYear)
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show

  override def numSeasonsAvailable: Option[Int] = {
    episodes
      .filter(_.nonEmpty)
      .map(episodes => episodes.map(_.seasonNumber).distinct.size)
  }

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
  override def url: Option[String] = {
    externalId.map(eid => {
      s"https://www.hulu.com/${makeHuluType(itemType)}/${makeHuluSlug(title, eid)}"
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

@JsonCodec
case class HuluScrapeEpisode(
  id: String,
  externalId: String,
  genres: List[String],
  description: Option[String],
  title: String,
  rating: Option[String],
  episodeNumber: Int,
  seasonNumber: Int,
  premiereDate: Option[String],
  duration: Option[Int])
