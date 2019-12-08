package com.teletracker.common.db.model

import java.util.UUID

case class ExternalId(
  id: Option[Int],
  thingId: Option[UUID],
  tvEpisodeId: Option[Int],
  tmdbId: Option[String],
  imdbId: Option[String],
  netflixId: Option[String],
  lastUpdatedAt: java.time.OffsetDateTime)
