package com.teletracker.common.availability

import com.teletracker.common.db.dynamo.model.StoredNetwork
import com.teletracker.common.db.model.{
  OfferType,
  PresentationType,
  SupportedNetwork
}
import com.teletracker.common.elasticsearch.model.{
  EsAvailability,
  EsAvailabilityLinks
}
import com.teletracker.common.util.OpenDateRange
import java.time.OffsetDateTime

object NetworkAvailability {
  def forSubscriptionNetwork(
    network: StoredNetwork,
    region: String = "US",
    availableWindow: OpenDateRange = OpenDateRange.infinite,
    presentationTypes: Set[PresentationType] = Set(PresentationType.SD,
      PresentationType.HD),
    availabilityLinks: Map[PresentationType, EsAvailabilityLinks] = Map.empty,
    numSeasonAvailable: Option[Int] = None,
    updateSource: Option[String] = None
  ): List[EsAvailability] = {
    presentationTypes.toList.sorted.map(typ => {
      EsAvailability(
        network_id = network.id,
        network_name = Some(network.name.toLowerCase),
        region = region,
        start_date = availableWindow.start,
        end_date = availableWindow.end,
        offer_type = OfferType.Subscription.toString.toLowerCase(),
        cost = None,
        currency = None,
        presentation_type = Some(typ.toString.toLowerCase),
        links = availabilityLinks.get(typ),
        num_seasons_available = numSeasonAvailable,
        last_updated = Some(OffsetDateTime.now()),
        last_updated_by = updateSource
      )
    })
  }

  def forSupportedNetwork(
    supportedNetwork: SupportedNetwork,
    storedNetwork: StoredNetwork,
    region: String = "US",
    availableWindow: OpenDateRange = OpenDateRange.infinite,
    presentationTypes: Set[PresentationType] = Set(PresentationType.SD,
      PresentationType.HD),
    availabilityLinks: Map[PresentationType, EsAvailabilityLinks] = Map.empty,
    numSeasonAvailable: Option[Int] = None,
    updateSource: Option[String] = None
  ): List[EsAvailability] = {
    supportedNetwork match {
      case SupportedNetwork.Netflix | SupportedNetwork.Hulu |
          SupportedNetwork.DisneyPlus | SupportedNetwork.Hbo |
          SupportedNetwork.HboMax | SupportedNetwork.AmazonPrimeVideo =>
        forSubscriptionNetwork(
          storedNetwork,
          region,
          availableWindow,
          presentationTypes,
          availabilityLinks,
          numSeasonAvailable,
          updateSource
        )

      case SupportedNetwork.AmazonVideo => ???
    }
  }
}
