package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.util.json.circe._
import com.teletracker.common.db.model.ThingType
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}
import java.time.format.DateTimeFormatter

class IngestHuluCatalog @Inject()(
  protected val tmdbClient: TmdbClient,
  protected val tmdbProcessor: TmdbEntityProcessor,
  protected val thingsDb: ThingsDbAccess,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater)
    extends IngestJob[HuluCatalogItem]
    with IngestJobWithElasticsearch[HuluCatalogItem] {

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def processMode(args: IngestJobArgs): ProcessMode =
    Parallel(32)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

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
  `type`: ThingType,
  externalId: Option[String],
  numSeasonsAvailable: Option[Int],
  genres: Option[List[String]])
    extends ScrapedItem {
  override def category: String = ""

  override def status: String = ""

  override def availableDate: Option[String] = availableOn

  override def title: String = name

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
}
