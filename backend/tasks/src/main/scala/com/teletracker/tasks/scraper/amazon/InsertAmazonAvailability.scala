package com.teletracker.tasks.scraper.amazon

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  ExternalSource,
  OfferType,
  PresentationType
}
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsAvailabilityLinks,
  EsExternalId,
  EsItem
}
import com.teletracker.common.elasticsearch.{
  ItemLookup,
  ItemLookupResponse,
  ItemUpdater
}
import com.teletracker.common.tasks.UntypedTeletrackerTask
import com.teletracker.common.util.NetworkCache
import com.teletracker.common.util.Futures._
import javax.inject.Inject
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.Try

class InsertAmazonAvailability @Inject()(
  itemLookup: ItemLookup,
  itemUpdater: ItemUpdater,
  networkCache: NetworkCache
)(implicit executionContext: ExecutionContext)
    extends UntypedTeletrackerTask {
  import diffson._
  import diffson.circe._
  import diffson.jsonpatch.lcsdiff.remembering._
  import diffson.lcs._
  import io.circe._
  import io.circe.syntax._

  implicit private val lcs = new Patience[Json]

  override protected def runInternal(): Unit = {
    val itemId = rawArgs.valueOrThrow[UUID]("itemId")
    val amzUrl = rawArgs.valueOrThrow[String]("amzUrl")
    val hdRentPrice = rawArgs.valueOrThrow[Double]("hdRentPrice")
    val sdRentPrice = rawArgs.valueOrDefault("sdRentPrice", hdRentPrice)
    val hdBuyPrice = rawArgs.valueOrThrow[Double]("hdBuyPrice")
    val sdBuyPrice = rawArgs.valueOrDefault("sdBuyPrice", hdBuyPrice)
    val freeOnPrime = rawArgs.valueOrDefault("freeOnPrime", false)
    val externalId = rawArgs.valueOrThrow[String]("externalId")
    val dryRun = rawArgs.valueOrDefault("dryRun", true)

    val networks = networkCache.getAllNetworks().await()

    val amazonNetworks =
      Set(ExternalSource.AmazonPrimeVideo, ExternalSource.AmazonVideo)

    val networksById = networks
      .map(net => net.slug.value -> net)
      .toMap
      .flatMap {
        case (name, network) =>
          Try(ExternalSource.fromString(name)).toOption.map(_ -> network)
      }
      .filter {
        case (network, _) =>
          amazonNetworks.contains(network)
      }

    assert(networksById.isDefinedAt(ExternalSource.AmazonPrimeVideo))
    assert(networksById.isDefinedAt(ExternalSource.AmazonVideo))

    itemLookup
      .lookupItem(
        Left(itemId),
        None,
        shouldMateralizeCredits = false,
        shouldMaterializeRecommendations = false
      )
      .map {
        case None =>
          throw new IllegalArgumentException(s"No item with id = ${itemId}")

        case Some(ItemLookupResponse(rawItem, _, _)) =>
          val amazonVideoNetwork = networksById(ExternalSource.AmazonVideo)
          val amazonPrimeNetwork = networksById(ExternalSource.AmazonPrimeVideo)

          val avs = List(
            createAvailabilities(
              amazonVideoNetwork,
              PresentationType.HD,
              OfferType.Rent,
              Some(hdRentPrice),
              amzUrl
            ),
            createAvailabilities(
              amazonVideoNetwork,
              PresentationType.SD,
              OfferType.Rent,
              Some(sdRentPrice),
              amzUrl
            ),
            createAvailabilities(
              amazonVideoNetwork,
              PresentationType.HD,
              OfferType.Buy,
              Some(hdBuyPrice),
              amzUrl
            ),
            createAvailabilities(
              amazonVideoNetwork,
              PresentationType.SD,
              OfferType.Buy,
              Some(sdBuyPrice),
              amzUrl
            )
          )

          val primeAvs = if (freeOnPrime) {
            List(
              createAvailabilities(
                amazonPrimeNetwork,
                PresentationType.HD,
                OfferType.Subscription,
                None,
                amzUrl
              ),
              createAvailabilities(
                amazonPrimeNetwork,
                PresentationType.SD,
                OfferType.Subscription,
                None,
                amzUrl
              )
            )
          } else {
            Nil
          }

          val allNewAvs = avs ++ primeAvs

          val newAvailabilities = rawItem.availability match {
            case Some(value) =>
              val duplicatesRemoved = value.filterNot(availability => {
                allNewAvs.exists(
                  EsAvailability.availabilityEquivalent(_, availability)
                )
              })

              duplicatesRemoved ++ allNewAvs

            case None =>
              allNewAvs
          }

          val newExternalIds = rawItem.externalIdsGrouped ++ Map(
            ExternalSource.AmazonVideo -> externalId
          )

          val newItem = rawItem.copy(
            availability = Some(newAvailabilities),
            external_ids = EsExternalId.fromMap(newExternalIds)
          )

          if (dryRun && rawItem != newItem) {
            logger.info(
              s"Would've updated id = ${newItem.id}:\n ${diff(rawItem.asJson, newItem.asJson).asJson.spaces2}"
            )
          } else if (rawItem != newItem) {
            logger.info(s"Updating availability for item id = ${newItem.id}")

            itemUpdater.update(newItem)
          }
      }
      .await()
  }

  private def createAvailabilities(
    network: StoredNetwork,
    presentationType: PresentationType,
    offerType: OfferType,
    cost: Option[Double],
    link: String
  ) = {
    EsAvailability(
      network_id = network.id,
      network_name = Some(network.name),
      region = "US",
      start_date = None,
      end_date = None,
      offer_type = offerType.getName,
      cost = cost,
      currency = Some("USD"),
      presentation_type = Some(presentationType.getName),
      links = Some(EsAvailabilityLinks(web = Some(link))),
      num_seasons_available = None
    )
  }
}
