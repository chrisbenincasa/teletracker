package com.teletracker.tasks.scraper.hbo

import com.teletracker.common.db.model.{ExternalSource, ItemType}
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.util.json.circe._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper._
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatching,
  ElasticsearchLookup,
  MatchMode
}
import com.teletracker.tasks.scraper.model.{MatchResult, NonMatchResult}
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.{LocalDate, OffsetDateTime}
import java.util.regex.Pattern
import scala.concurrent.Future

class IngestHboCatalog @Inject()(
  protected val teletrackerConfig: TeletrackerConfig,
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
      .flatMap {
        case (matches, _) =>
          val amendedMatches = matches.map(_.scrapedItem)
          val missing = originalByAmended.collect {
            case (amended, original) if !amendedMatches.contains(amended) =>
              original
          }

          super
            .handleNonMatches(args, missing.toList)
            .map(_ => {
              val potentialMatches = matches.map {
                case MatchResult(amended, esItem) =>
                  esItem -> originalByAmended(amended)
              }

              writePotentialMatches(potentialMatches)

              matches.map {
                case MatchResult(amended, esItem) =>
                  model.NonMatchResult(
                    amended,
                    originalByAmended(amended),
                    esItem
                  )
              }
            })
      }
  }

  override protected def itemUniqueIdentifier(item: HboCatalogItem): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }

  override protected def getExternalIds(
    item: HboCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.Hbo -> item.externalId
    ).collect {
      case (k, Some(v)) => k -> v
    }
  }
}

@JsonCodec
case class HboCatalogItem(
  name: String,
  nameFallback: Option[String],
  releaseYear: Option[Int],
  network: String,
  `type`: ItemType,
  externalId: Option[String],
  genres: Option[List[String]],
  description: Option[String])
    extends ScrapedItem {

  override def category: Option[String] = None

  override def status: String = ""

  override def availableDate: Option[String] = None

  override def title: String = name

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override lazy val availableLocalDate: Option[LocalDate] =
    availableDate.map(OffsetDateTime.parse(_)).map(_.toLocalDate)
}
