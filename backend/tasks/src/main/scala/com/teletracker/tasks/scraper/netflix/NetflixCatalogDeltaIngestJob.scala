package com.teletracker.tasks.scraper.netflix

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  PresentationType
}
import com.teletracker.common.elasticsearch.{
  EsAvailability,
  ItemLookup,
  ItemUpdater
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.{
  ElasticsearchFallbackMatcher,
  ElasticsearchFallbackMatcherOptions,
  ElasticsearchLookup
}
import com.teletracker.tasks.scraper.model.NonMatchResult
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  IngestDeltaJobArgs,
  IngestJobArgs,
  IngestJobParser
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID
import scala.concurrent.Future

case class NetflixCatalogDeltaIngestJob @Inject()(
  s3: S3Client,
  networkCache: NetworkCache,
  protected val itemLookup: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup,
  elasticsearchFallbackMatcher: ElasticsearchFallbackMatcher.Factory)
    extends IngestDeltaJob[NetflixCatalogItem](elasticsearchLookup) {

  override protected val networkNames: Set[String] = Set("netflix")
  override protected val externalSource: ExternalSource = ExternalSource.Netflix

  private val elasticsearchMatcherOptions =
    ElasticsearchFallbackMatcherOptions(
      requireTypeMatch = false,
      getClass.getSimpleName
    )

  private lazy val fallbackMatcher = elasticsearchFallbackMatcher
    .create(elasticsearchMatcherOptions)

//  prerun {
//    registerArtifact(fallbackMatcher.outputFile)
//  }

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    itemId: UUID,
    title: String,
    scrapedItem: NetflixCatalogItem,
    isAvailable: Boolean
  ): List[EsAvailability] = {
    networks.toList.map(network => {
      EsAvailability(
        network_id = network.id,
        region = "US",
        start_date = None,
        end_date = None,
        offer_type = OfferType.Subscription.toString,
        cost = None,
        currency = None,
        presentation_types = Some(
          List(PresentationType.SD, PresentationType.HD).map(_.toString)
        ) // TODO FIX
      )
    })
  }

  override protected def handleNonMatches(
    args: IngestDeltaJobArgs,
    nonMatches: List[NetflixCatalogItem]
  ): Future[List[NonMatchResult[NetflixCatalogItem]]] = {
    fallbackMatcher
      .handleNonMatches(
        args,
        nonMatches
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

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: NetflixCatalogItem): String =
    item.externalId.get
}
