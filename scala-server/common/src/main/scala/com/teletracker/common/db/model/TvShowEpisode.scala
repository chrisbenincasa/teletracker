package com.teletracker.common.db.model

import com.teletracker.common.util.json.circe._
import io.circe.generic.JsonCodec
import java.util.UUID

case class TvShowEpisode(
  id: Option[Int],
  number: Int,
  thingId: UUID,
  seasonId: Int,
  name: String,
  productionCode: Option[String]) {
  def withAvailability(
    availability: Availability
  ): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, Some(availability))
  }

  def withAvailability(
    availability: Option[Availability]
  ): TvShowEpisodeWithAvailability = {
    TvShowEpisodeWithAvailability(id, number, seasonId, availability)
  }
}

@JsonCodec case class TvShowEpisodeWithAvailability(
  id: Option[Int],
  number: Int,
  seasonId: Int,
  availability: Option[Availability])
