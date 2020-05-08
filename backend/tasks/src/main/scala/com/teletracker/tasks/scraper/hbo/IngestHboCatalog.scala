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
import com.teletracker.tasks.scraper.matching.ElasticsearchFallbackMatching
import io.circe.generic.JsonCodec
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate, LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.regex.Pattern

class IngestHboCatalog @Inject()(
  protected val teletrackerConfig: TeletrackerConfig,
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  protected val elasticsearchExecutor: ElasticsearchExecutor)
    extends IngestJob[HboScrapedCatalogItem]
    with ElasticsearchFallbackMatching[HboScrapedCatalogItem] {

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.HboGo, ExternalSource.HboNow)

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def isAvailable(
    item: HboScrapedCatalogItem,
    today: LocalDate
  ): Boolean = {
    true
  }

  private val pattern =
    Pattern.compile("Director's Cut", Pattern.CASE_INSENSITIVE)
  override protected def sanitizeItem(
    item: HboScrapedCatalogItem
  ): HboScrapedCatalogItem = {
    item.copy(title = pattern.matcher(item.title).replaceAll("").trim)
  }
//
//  override protected def handleNonMatches(
//    args: IngestJobArgs,
//    nonMatches: List[HboScrapedCatalogItem]
//  ): Future[List[NonMatchResult[HboScrapedCatalogItem]]] = {
//    val withFallback = nonMatches
//      .filter(_.nameFallback.isDefined)
//      .filterNot(m => m.name == m.nameFallback.get)
//
//    val originalByAmended = withFallback
//      .map(
//        item =>
//          item.copy(
//            name = item.nameFallback.get,
//            nameFallback = Some(item.name)
//          ) -> item
//      )
//      .toMap
//
//    lookupMethod
//      .apply(originalByAmended.keys.toList, args)
//      .flatMap {
//        case (matches, _) =>
//          val amendedMatches = matches.map(_.scrapedItem)
//          val missing = originalByAmended.collect {
//            case (amended, original) if !amendedMatches.contains(amended) =>
//              original
//          }
//
//          super
//            .handleNonMatches(args, missing.toList)
//            .map(_ => {
//              val potentialMatches = matches.map {
//                case MatchResult(amended, esItem) =>
//                  esItem -> originalByAmended(amended)
//              }
//
//              writePotentialMatches(potentialMatches)
//
//              matches.map {
//                case MatchResult(amended, esItem) =>
//                  model.NonMatchResult(
//                    amended,
//                    originalByAmended(amended),
//                    esItem
//                  )
//              }
//            })
//      }
//  }

  override protected def itemUniqueIdentifier(
    item: HboScrapedCatalogItem
  ): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }

  override protected def getExternalIds(
    item: HboScrapedCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.HboGo -> item.externalId,
      ExternalSource.HboNow -> item.externalId
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

@JsonCodec
case class HboScrapedCatalogItem(
  title: String,
  description: Option[String],
  itemType: ItemType,
  id: Option[String],
  goUrl: Option[String],
  nowUrl: Option[String],
  network: String,
  releaseDate: Option[String])
    extends ScrapedItem {
  override def availableDate: Option[String] = None
  override lazy val releaseYear: Option[Int] =
    releaseDate.map(Instant.parse(_).atOffset(ZoneOffset.UTC).getYear)
  override def category: Option[String] = None
  override def status: String = ""
  override def externalId: Option[String] = id
  override def isMovie: Boolean = itemType == ItemType.Movie
  override def isTvShow: Boolean = itemType == ItemType.Show
}
