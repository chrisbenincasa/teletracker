package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model._
import com.teletracker.common.elasticsearch.{
  ElasticsearchExecutor,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.external.tmdb.TmdbClient
import com.teletracker.common.process.tmdb.TmdbEntityProcessor
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.common.util.execution.SequentialFutures
import com.teletracker.tasks.scraper.IngestJobParser.{JsonPerLine, ParseMode}
import com.teletracker.tasks.scraper.matching.{ElasticsearchLookup, MatchMode}
import com.teletracker.tasks.scraper.model.{
  NonMatchResult,
  WhatsOnNetflixCatalogItem
}
import com.teletracker.tasks.util.SourceRetriever
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.parser._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.LocalDate
import java.util.UUID
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
  protected val elasticsearchExecutor: ElasticsearchExecutor,
  elasticsearchFallbackMatcher: ElasticsearchFallbackMatcher.Factory,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestJob[NetflixCatalogItem] {
  override protected def networkNames: Set[String] = Set("netflix")

  override protected def parseMode: ParseMode = JsonPerLine

  override protected def matchMode: MatchMode =
    elasticsearchLookup

  private val elasticsearchMatcherOptions =
    ElasticsearchFallbackMatcherOptions(false, getClass.getSimpleName)

  private val alternateItemsByNetflixId =
    new mutable.HashMap[String, WhatsOnNetflixCatalogItem]()

  override protected def preprocess(
    args: IngestJobArgs,
    rawArgs: Args
  ): Unit = {
    val alternateMovieCatalogUri =
      rawArgs.valueOrThrow[URI]("alternateMovieCatalog")
    val alternateShowCatalogUri =
      rawArgs.valueOrThrow[URI]("alternateTvCatalog")

    val sourceRetriever = new SourceRetriever(s3)

    val alternateMovieSource =
      sourceRetriever.getSource(alternateMovieCatalogUri)

    val alternateTvSource =
      sourceRetriever.getSource(alternateShowCatalogUri)

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

    loadItemsOrThrow(alternateMovieSource)
    loadItemsOrThrow(alternateTvSource)
  }

  override protected def handleNonMatches(
    args: IngestJobArgs,
    nonMatches: List[NetflixCatalogItem]
  ): Future[List[NonMatchResult[NetflixCatalogItem]]] = {
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

    elasticsearchFallbackMatcher
      .create(elasticsearchMatcherOptions)
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
  }

  override protected def isAvailable(
    item: NetflixCatalogItem,
    today: LocalDate
  ): Boolean = true
}

@JsonCodec
case class NetflixCatalogItem(
  availableDate: Option[String],
  title: String,
  releaseYear: Option[Int],
  network: String,
  `type`: ThingType,
  externalId: Option[String])
    extends ScrapedItem {
  val status = "Available"

  override def category: String = ""

  override def isMovie: Boolean = `type` == ThingType.Movie

  override def isTvShow: Boolean = `type` == ThingType.Show
}
