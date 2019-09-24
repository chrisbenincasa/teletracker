package com.teletracker.tasks.scraper

import com.google.cloud.storage.Storage
import com.teletracker.common.db.access.ThingsDbAccess
import com.teletracker.common.db.model.{
  Availability,
  Network,
  OfferType,
  PresentationType,
  ThingRaw
}
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.json.circe._
import com.teletracker.tasks.scraper.IngestJobParser.JsonPerLine
import io.circe.generic.auto._
import javax.inject.Inject

case class NetflixCatalogDeltaIngestJob @Inject()(
  storage: Storage,
  thingsDbAccess: ThingsDbAccess,
  networkCache: NetworkCache)
    extends IngestDeltaJob[UnogsNetflixCatalogItem] {

  override protected def networkNames: Set[String] = Set("netflix")

  override protected def createAvailabilities(
    networks: Set[Network],
    thing: ThingRaw,
    scrapedItem: UnogsNetflixCatalogItem,
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
        thingId = Some(thing.id),
        tvShowEpisodeId = None,
        networkId = network.id,
        presentationType = Some(presentationType)
      )
    }
  }

  override protected def parseMode: IngestJobParser.ParseMode = JsonPerLine

  override protected def uniqueKey(item: UnogsNetflixCatalogItem): String =
    item.externalId.get
}
