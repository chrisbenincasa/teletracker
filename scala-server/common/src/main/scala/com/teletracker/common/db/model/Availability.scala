package com.teletracker.common.db.model

import java.time.OffsetDateTime
import java.util.UUID

case class Availability(
  id: Option[Int],
  isAvailable: Boolean,
  region: Option[String],
  numSeasons: Option[Int],
  startDate: Option[OffsetDateTime],
  endDate: Option[OffsetDateTime],
  offerType: Option[OfferType],
  cost: Option[BigDecimal],
  currency: Option[String],
  thingId: Option[UUID],
  tvShowEpisodeId: Option[Int],
  networkId: Option[Int],
  presentationType: Option[PresentationType]) {
  def toDetailed: AvailabilityWithDetails = {
    AvailabilityWithDetails(
      id,
      isAvailable,
      region,
      numSeasons,
      startDate,
      endDate,
      offerType,
      cost,
      currency,
      presentationType,
      thingId,
      tvShowEpisodeId,
      networkId,
      None
    )
  }

  def withNetwork(network: Network): AvailabilityWithDetails = {
    AvailabilityWithDetails(
      id,
      isAvailable,
      region,
      numSeasons,
      startDate,
      endDate,
      offerType,
      cost,
      currency,
      presentationType,
      thingId,
      tvShowEpisodeId,
      networkId,
      Some(network)
    )
  }

  def matches(other: Availability): Boolean = {
    val idsEqual = (for (tid <- thingId; tid2 <- other.thingId)
      yield tid == tid2).getOrElse(false)

    val episodeIdsEqual =
      (for (tid <- tvShowEpisodeId; tid2 <- other.tvShowEpisodeId)
        yield tid == tid2).getOrElse(false)

    val networkIdEqual = (for (tid <- networkId; tid2 <- other.networkId)
      yield tid == tid2).getOrElse(false)

    val offerTypeEqual = (for (tid <- offerType; tid2 <- other.offerType)
      yield tid == tid2).getOrElse(false)

    (idsEqual ^ episodeIdsEqual) && networkIdEqual && offerTypeEqual
  }
}

case class AvailabilityWithDetails(
  id: Option[Int],
  isAvailable: Boolean,
  region: Option[String],
  numSeasons: Option[Int],
  startDate: Option[OffsetDateTime],
  endDate: Option[OffsetDateTime],
  offerType: Option[OfferType],
  cost: Option[BigDecimal],
  currency: Option[String],
  presentationType: Option[PresentationType],
  thingId: Option[UUID],
  tvShowEpisodeId: Option[Int],
  networkId: Option[Int],
  network: Option[Network] = None,
  thing: Option[PartialThing] = None) {

  def withNetwork(network: Network): AvailabilityWithDetails =
    this.copy(network = Some(network))

  def withThing(thing: PartialThing): AvailabilityWithDetails =
    this.copy(thing = Some(thing))
}
