package com.teletracker.tasks.scraper.debug

import com.teletracker.common.availability.NetworkAvailability
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import com.teletracker.common.model.scraping.{PotentialMatch, ScrapeItemType}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.scraper.{ScrapeItemStreams, TypeWithParsedJson}
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import java.net.URI
import java.time.OffsetDateTime
import scala.util.Try

class ImportPotentialMatchesToEs @Inject()(
  sourceRetriever: SourceRetriever,
  networkCache: NetworkCache,
  esPotentialMatchItemStore: EsPotentialMatchItemStore)
    extends UntypedTeletrackerTask {
  override protected def runInternal(): Unit = {
    val input = rawArgs.valueOrThrow[URI]("input")
    val itemType = rawArgs.valueOrThrow[ScrapeItemType]("itemType")
    val limit = rawArgs.valueOrDefault("limit", -1)
    val dryRun = rawArgs.valueOrDefault[Boolean]("dryRun", true)

    val networks = networkCache.getAllNetworks().await()
    val supportedNetworks =
      scrapeItemTypeToSupportedNetworks(itemType).flatMap(network => {
        networks
          .find(
            storedNetwork =>
              Try(SupportedNetwork.fromString(storedNetwork.slug.value)).toOption
                .contains(network)
          )
          .map(network -> _)
      })

    val now = OffsetDateTime.now()

    sourceRetriever
      .getSourceStream(input)
      .foreach(source => {
        ScrapeItemStreams
          .getPotentialMatchResultWithParsedStream(source, itemType)
          .collect {
            case Right(value) => value
          }
          .safeTake(limit)
          .grouped(25)
          .map(_.toList)
          .foreach(group => {
            val potential = group.collect {
              case TypeWithParsedJson(
                  PotentialMatch(potential, scraped),
                  parsedJson
                  ) if scraped.externalId.isDefined =>
                val availabilities = supportedNetworks.toList.flatMap {
                  case (supportedNetwork, storedNetwork) =>
                    NetworkAvailability.forSupportedNetwork(
                      supportedNetwork,
                      storedNetwork,
                      numSeasonAvailable = scraped.numSeasonsAvailable
                    )
                }

                EsPotentialMatchItem(
                  id = EsPotentialMatchItem.id(
                    potential.id,
                    EsExternalId(
                      scrapeItemTypeToExternalSource(itemType),
                      scraped.externalId.get
                    )
                  ),
                  created_at = now,
                  state = EsPotentialMatchState.Unmatched,
                  last_updated = now,
                  potential = potential,
                  scraped = EsGenericScrapedItem(
                    `type` = itemType,
                    item = EsScrapedItem.fromAnyScrapedItem(scraped),
                    raw = parsedJson.asObject
                      .flatMap(_.apply("scraped"))
                      .getOrElse(io.circe.Json.Null)
                  ),
                  availability = Some(availabilities)
                )
            }

            if (dryRun) {
              import io.circe.syntax._
              potential.foreach(item => {
                logger.info(s"Would've upserted item:\n${item.asJson.spaces2}")
              })
            } else {
              esPotentialMatchItemStore.upsertBatch(potential).await()
            }
          })
      })
  }

  private def scrapeItemTypeToExternalSource(itemType: ScrapeItemType) = {
    itemType match {
      case ScrapeItemType.HuluCatalog              => ExternalSource.Hulu
      case ScrapeItemType.HboCatalog               => ExternalSource.HboGo
      case ScrapeItemType.NetflixCatalog           => ExternalSource.Netflix
      case ScrapeItemType.DisneyPlusCatalog        => ExternalSource.DisneyPlus
      case ScrapeItemType.HboMaxCatalog            => ExternalSource.HboMax
      case ScrapeItemType.HboChanges               => ExternalSource.HboGo
      case ScrapeItemType.NetflixOriginalsArriving => ExternalSource.Netflix
    }
  }

  private def scrapeItemTypeToSupportedNetworks(itemType: ScrapeItemType) = {
    itemType match {
      case ScrapeItemType.HuluCatalog => Set(SupportedNetwork.Hulu)
      case ScrapeItemType.HboCatalog =>
        Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)
      case ScrapeItemType.NetflixCatalog    => Set(SupportedNetwork.Netflix)
      case ScrapeItemType.DisneyPlusCatalog => Set(SupportedNetwork.DisneyPlus)
      case ScrapeItemType.HboMaxCatalog     => Set(SupportedNetwork.HboMax)
      case ScrapeItemType.HboChanges =>
        Set(SupportedNetwork.Hbo, SupportedNetwork.HboMax)
      case ScrapeItemType.NetflixOriginalsArriving =>
        Set(SupportedNetwork.Netflix)
    }
  }
}
