package com.teletracker.tasks.scraper

import com.teletracker.common.util.json.circe._
import com.teletracker.common.db.model.{ThingRaw, ThingType}
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import com.teletracker.tasks.scraper.model.{MatchResult, NonMatchResult}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}
import java.util.regex.Pattern
import scala.concurrent.Future

class IngestHboCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HboCatalogItem]
    with ElasticsearchFallbackMatching[HboCatalogItem] {

  override protected def networkNames: Set[String] = Set("hbo-now", "hbo-go")

  override protected def matchMode: MatchMode = elasticsearchLookup

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def isAvailable(
    item: HboCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  private val pattern =
    Pattern.compile("Director's Cut", Pattern.CASE_INSENSITIVE)
  override protected def sanitizeItem(item: HboCatalogItem): HboCatalogItem = {
    item.copy(name = pattern.matcher(item.name).replaceAll("").trim)
  }

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[HboCatalogItem]
  ): Future[List[NonMatchResult[HboCatalogItem]]] = {
    val withFallback = nonMatches
      .filter(_.nameFallback.isDefined)
      .filterNot(m => m.name == m.nameFallback.get)

    val originalByAmended = withFallback
      .map(
        item =>
          item.copy(
            name = item.nameFallback.get,
            nameFallback = Some(item.name)
          ) -> item
      )
      .toMap

    matchMode
      .lookup(originalByAmended.keys.toList, args)
      .map {
        case (matches, _) =>
          matches.map {
            case MatchResult(amended, esItem) =>
              model.NonMatchResult(
                amended,
                originalByAmended(amended),
                esItem
              )
          }
      }
  }
}

@JsonCodec
case class HboCatalogItem(
  name: String,
  nameFallback: Option[String],
  releaseYear: Option[Int],
  network: String,
  `type`: ThingType,
  externalId: Option[String],
  genres: Option[List[String]])
    extends ScrapedItem {

  override def category: String = ""

  override def status: String = ""

  override def availableDate: Option[String] = None

  override def title: String = name

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
}
