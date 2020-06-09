package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.model.scraping.{
  NonMatchResult,
  ScrapeItemType,
  ScrapedItem
}
import com.teletracker.common.model.scraping.netflix.NetflixScrapedCatalogItem
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.{JsonPerLine, ParseMode}
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatcher,
  ElasticsearchFallbackMatcherOptions
}
import com.teletracker.tasks.scraper.model.WhatsOnNetflixCatalogItem
import com.teletracker.tasks.scraper.{
  IngestJob,
  IngestJobArgs,
  IngestJobArgsLike,
  SubscriptionNetworkAvailability
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import io.circe.parser._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.Future
import scala.io.Source

case class IngestNetflixCatalogJobArgs(
  inputFile: URI,
  offset: Int,
  limit: Int,
  titleMatchThreshold: Int,
  dryRun: Boolean,
  parallelism: Int,
  sourceLimit: Int)
    extends IngestJobArgsLike

class IngestNetflixCatalog @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchFallbackMatcher: ElasticsearchFallbackMatcher.Factory)
    extends IngestJob[NetflixScrapedCatalogItem]
    with SubscriptionNetworkAvailability[NetflixScrapedCatalogItem] {

  override protected def scrapeItemType: ScrapeItemType =
    ScrapeItemType.NetflixCatalog

  override protected def externalSources: List[ExternalSource] =
    List(ExternalSource.Netflix)

  override protected def parseMode: ParseMode = JsonPerLine

  private val elasticsearchMatcherOptions =
    ElasticsearchFallbackMatcherOptions(
      requireTypeMatch = false,
      getClass.getSimpleName
    )

  private lazy val fallbackMatcher = elasticsearchFallbackMatcher
    .create(elasticsearchMatcherOptions)

  private val alternateItemsByNetflixId =
    new mutable.HashMap[String, WhatsOnNetflixCatalogItem]()

  private val seenItems = ConcurrentHashMap.newKeySet[String]()

  override protected def shouldProcessItem(
    item: NetflixScrapedCatalogItem
  ): Boolean = {
    item.externalId.forall(seenItems.add)
  }

  override protected def preprocess(): Unit = {
    val alternateMovieCatalogUri =
      rawArgs.value[URI]("alternateMovieCatalog")
    val alternateShowCatalogUri =
      rawArgs.value[URI]("alternateTvCatalog")

    val sourceRetriever = new SourceRetriever(s3)

    def loadItemsOrThrow(source: Source): Unit = {
      try {
        decode[List[WhatsOnNetflixCatalogItem]](
          source.getLines().mkString("")
        ) match {
          case Left(value) =>
            logger.error(value.getMessage)
            throw value

          case Right(value) =>
            value
              .map(item => item.netflixid -> item)
              .foreach(alternateItemsByNetflixId += _)
        }
      } finally {
        source.close()
      }
    }

    alternateMovieCatalogUri
      .map(sourceRetriever.getSource(_))
      .foreach(source => {
        loadItemsOrThrow(source)
      })

    alternateShowCatalogUri
      .map(sourceRetriever.getSource(_))
      .foreach(source => {
        loadItemsOrThrow(source)
      })
  }

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[NetflixScrapedCatalogItem]
  ): Future[List[NonMatchResult[NetflixScrapedCatalogItem]]] = {
    val (hasAlternate, doesntHaveAlternate) = nonMatches.partition(
      item =>
        item.externalId.isDefined && alternateItemsByNetflixId
          .isDefinedAt(item.externalId.get)
    )

    val alternateItems = hasAlternate.map(original => {
      val alternate = alternateItemsByNetflixId(original.externalId.get)
      original.copy(
        title =
          if (alternate.title != original.title) alternate.title
          else original.title,
        releaseYear = Some(alternate.titlereleased.toInt)
      )
    })

    fallbackMatcher
      .handleNonMatches(
        args,
        alternateItems ++ doesntHaveAlternate
      )
      .map(results => {
        results.map(result => {
          result.amendedScrapedItem.externalId match {
            case Some(value) =>
              result.copy(
                originalScrapedItem = nonMatches
                  .find(_.externalId.contains(value))
                  .getOrElse(result.amendedScrapedItem)
              )

            case None => result
          }
        })
      })
      .map(results => {
        writePotentialMatches(results.map(result => {
          result.esItem -> result.originalScrapedItem
        }))
        Nil
      })
  }

  override protected def isAvailable(
    item: NetflixScrapedCatalogItem,
    today: LocalDate
  ): Boolean = true

  override protected def itemUniqueIdentifier(
    item: NetflixScrapedCatalogItem
  ): String = {
    item.externalId.getOrElse(super.itemUniqueIdentifier(item))
  }

  override protected def getExternalIds(
    item: NetflixScrapedCatalogItem
  ): Map[ExternalSource, String] = {
    Map(
      ExternalSource.Netflix -> item.externalId
    ).collect {
      case (k, Some(v)) => k -> v
    }
  }
}

@JsonCodec
case class NetflixCatalogItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  `type`: ItemType,
  externalId: Option[String],
  description: Option[String])
    extends ScrapedItem {
  val status = "Available"

  override def category: Option[String] = None

  override def isMovie: Boolean = `type` == ItemType.Movie

  override def isTvShow: Boolean = `type` == ItemType.Show

  override def url: Option[String] =
    externalId.map(id => s"https://netflix.com/title/$id")
}
