package com.teletracker.common.db.model

import io.circe.generic.JsonCodec
import java.time.LocalDate
import java.util.UUID

case class TvShowSeason(
  id: Option[Int],
  number: Int,
  showId: UUID,
  overview: Option[String],
  airDate: Option[LocalDate])

@JsonCodec case class TvShowSeasonWithEpisodes(
  id: Option[Int],
  number: Int,
  overview: Option[String],
  airDate: Option[LocalDate],
  episodes: Option[List[TvShowEpisodeWithAvailability]])
