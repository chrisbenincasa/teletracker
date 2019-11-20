package com.teletracker.tasks.scraper

import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  Availability,
  Network,
  OfferType,
  PresentationType,
  ThingRaw
}
import com.teletracker.common.elasticsearch.{ItemLookup, ItemUpdater}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.auto._
import javax.inject.Inject
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

case class NetflixCatalogDeltaIngestJob @Inject()(
  s3: S3Client,
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache,
  protected val itemSearch: ItemLookup,
  protected val itemUpdater: ItemUpdater)
    extends IngestDeltaJob[NetflixCatalogItem]
    with IngestDeltaJobWithElasticsearch[NetflixCatalogItem] {

  override protected def networkNames: Set[String] = Set("netflix")

  override protected def createAvailabilities(
    networks: Set[Network],
    itemId: UUID,
    title: String,
    scrapedItem: NetflixCatalogItem,
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

  override protected def uniqueKey(item: NetflixCatalogItem): String =
    item.externalId.get
}
