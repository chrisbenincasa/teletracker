package com.teletracker.tasks.scraper.debug

import com.teletracker.common.availability.NetworkAvailability
import com.teletracker.common.db.model.{ExternalSource, SupportedNetwork}
import com.teletracker.common.elasticsearch.model._
import com.teletracker.common.elasticsearch.scraping.EsPotentialMatchItemStore
import UpdateableEsItem.syntax._
import com.teletracker.common.config.TeletrackerConfig
import com.teletracker.common.elasticsearch.ItemLookup
import com.teletracker.common.model.scraping.{PotentialMatch, ScrapeItemType}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.Futures._
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.Lists._
import com.teletracker.tasks.scraper.{ScrapeItemStreams, TypeWithParsedJson}
import com.teletracker.tasks.util.SourceRetriever
import javax.inject.Inject
import org.elasticsearch.index.query.{QueryBuilder, QueryBuilders}
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, Executors}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try

class BackfillPotentialPopularity @Inject()(
  teletrackerConfig: TeletrackerConfig,
  esPotentialMatchItemStore: EsPotentialMatchItemStore,
  itemLookup: ItemLookup
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  import io.circe.syntax._

  override protected def runInternal(): Unit = {
    val limit = rawArgs.valueOrDefault("limit", -1)

    val scheduler = Executors.newSingleThreadScheduledExecutor()
    val seenPopularities = new ConcurrentHashMap[UUID, Double]()

    esPotentialMatchItemStore.scroller
      .start(
        QueryBuilders.matchAllQuery()
      )
      .safeTake(limit)
      .grouped(25)
      .delayedForeachF(100 millis, scheduler)(batch => {
        itemLookup
          .lookupItemsByIds(batch.map(_.potential.id).toSet)
          .flatMap(items => {
            val foundItems = items.collect {
              case (uuid, Some(item)) => uuid -> item
            }

            seenPopularities.putAll(
              foundItems.collect {
                case (uuid, item) if item.popularity.isDefined =>
                  uuid -> item.popularity.get
              }.asJava
            )

            val updates = for {
              (itemId, scrapeItems) <- batch.groupBy(_.potential.id)
              item <- foundItems.get(itemId).toList
              scrapeItem <- scrapeItems
            } yield {
              if (item.popularity.isDefined) {
                val update = Map(
                  "potential" -> Map(
                    "popularity" -> item.popularity.get.asJson
                  )
                ).asJson

                logger.info(s"Updating ${scrapeItem.id}: ${update}")

                esPotentialMatchItemStore.partialUpdate(
                  scrapeItem.id,
                  update
                )
              } else {
                Future.unit
              }
            }

            Future.sequence(updates).map(_ => {})
          })
      })
      .await()
  }
}

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
                      numSeasonAvailable = scraped.numSeasonsAvailable,
                      updateSource = Some(getClass.getSimpleName)
                    )
                }

                val insert = EsPotentialMatchItem(
                  id = EsPotentialMatchItem.id(
                    potential.id,
                    EsExternalId(
                      scrapeItemTypeToExternalSource(itemType),
                      scraped.externalId.get
                    )
                  ),
                  created_at = now,
                  state = EsPotentialMatchState.Unmatched,
                  last_updated_at = now,
                  last_state_change = now,
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

                val update: EsPotentialMatchItemUpdateView = insert.toUpdateable

                update.copy(state = None, last_state_change = None) -> insert
            }

            if (dryRun) {
              import io.circe.syntax._
              potential.foreach(item => {
                logger.info(s"Would've upserted item:\n${item.asJson.spaces2}")
              })
            } else {
              esPotentialMatchItemStore
                .upsertBatchWithFallback(potential)
                .await()
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
