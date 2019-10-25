package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  Availability,
  Network,
  OfferType,
  PresentationType,
  ThingRaw
}
import com.teletracker.common.elasticsearch.{ItemSearch, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

class HuluCatalogDeltaIngestJob @Inject()(
  protected val s3: S3Client,
  protected val thingsDbAccess: ThingsDbAccess,
  protected val networkCache: NetworkCache,
  protected val itemSearch: ItemSearch,
  protected val itemUpdater: ItemUpdater)
    extends IngestDeltaJob[HuluCatalogItem]
    with IngestDeltaJobWithElasticsearch[HuluCatalogItem] {

  override protected def networkNames: Set[String] = Set("hulu")

  override protected def createAvailabilities(
    networks: Set[Network],
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
        networkId = network.id,
        presentationType = Some(presentationType)
      )
    }
  }

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: HuluCatalogItem): String =
    item.externalId.get
}
