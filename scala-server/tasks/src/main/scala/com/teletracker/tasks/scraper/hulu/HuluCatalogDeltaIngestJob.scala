package com.teletracker.tasks.scraper.hulu

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  Availability,
  Network,
  OfferType,
  PresentationType
}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import com.teletracker.tasks.scraper.matching.ElasticsearchLookup
import com.teletracker.tasks.scraper.{
  IngestDeltaJob,
  IngestDeltaJobWithElasticsearch,
  IngestJobParser
}
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

class HuluCatalogDeltaIngestJob @Inject()(
  protected val s3: S3Client,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater,
  elasticsearchLookup: ElasticsearchLookup)
    extends IngestDeltaJob[HuluCatalogItem](elasticsearchLookup)
    with IngestDeltaJobWithElasticsearch[HuluCatalogItem] {

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def createAvailabilities(
    networks: Set[StoredNetwork],
    itemId: UUID,
    title: String,
    scrapedItem: HuluCatalogItem,
    isAvailable: Boolean
  ): List[Availability] = {
    for {
      network <- networks.toList
      presentationType <- List(PresentationType.SD, PresentationType.HD)
    } yield {
      Availability(
        None,
        isAvailable = isAvailable,
        region = Some("US"),
        numSeasons = None,
        startDate = None,
        endDate = None,
        offerType = Some(OfferType.Subscription),
        cost = None,
        currency = None,
        thingId = Some(itemId),
        tvShowEpisodeId = None,
        networkId = Some(network.id),
        presentationType = Some(presentationType)
      )
    }
  }

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: HuluCatalogItem): String =
    item.externalId.get
}
