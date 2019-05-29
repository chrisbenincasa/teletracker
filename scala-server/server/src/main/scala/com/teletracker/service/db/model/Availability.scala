package com.teletracker.service.db.model

import com.teletracker.service.db.CustomPostgresProfile
import com.teletracker.service.inject.DbImplicits
import javax.inject.Inject
import java.time.OffsetDateTime
import com.teletracker.service.util.json.circe._

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
  thingId: Option[Int],
  tvShowEpisodeId: Option[Int],
  networkId: Option[Int],
  presentationType: Option[PresentationType]) {
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

    (idsEqual || episodeIdsEqual) && networkIdEqual && offerTypeEqual
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
  thingId: Option[Int],
  tvShowEpisodeId: Option[Int],
  networkId: Option[Int],
  network: Option[Network])

class Availabilities @Inject()(
  val driver: CustomPostgresProfile,
  val things: Things,
  val episodes: TvShowEpisodes,
  val networks: Networks,
  val implicits: DbImplicits) {
  import driver.api._
  import implicits._

  class AvailabilitiesTable(tag: Tag)
      extends Table[Availability](tag, "availability") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def isAvailable = column[Boolean]("is_available")
    def region = column[Option[String]]("region")
    def numSeasons = column[Option[Int]]("num_seasons")
    def startDate = column[Option[OffsetDateTime]]("start_date")
    def endDate = column[Option[OffsetDateTime]]("end_date")
    def offerType = column[Option[OfferType]]("offer_type")
    def cost = column[Option[BigDecimal]]("cost")
    def currency = column[Option[String]]("currency")
    def thingId = column[Option[Int]]("thing_id")
    def tvShowEpisodeId = column[Option[Int]]("tv_show_episode_id")
    def networkId = column[Option[Int]]("network_id")
    def presentationType = column[Option[PresentationType]]("presentation_type")

    def endDate_idx = index("availability_end_date_idx", endDate)
    def thingIdNetworkId =
      index("availability_thing_id_networkid", (thingId, networkId))

    def thingId_fk =
      foreignKey("availability_thing_id_fk", thingId, things.query)(_.id.?)
    def tvShowEpisodeId_fk =
      foreignKey(
        "availability_tv_show_episode_id_fk",
        tvShowEpisodeId,
        episodes.query
      )(_.id.?)
    def networkId_fk =
      foreignKey("availability_network_id_fk", networkId, networks.query)(
        _.id.?
      )

    override def * =
      (
        id.?,
        isAvailable,
        region,
        numSeasons,
        startDate,
        endDate,
        offerType,
        cost,
        currency,
        thingId,
        tvShowEpisodeId,
        networkId,
        presentationType
      ) <> (Availability.tupled, Availability.unapply)
  }

  val query = TableQuery[AvailabilitiesTable]
}
