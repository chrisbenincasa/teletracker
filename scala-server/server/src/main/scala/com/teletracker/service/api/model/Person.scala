package com.teletracker.service.api.model

import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.generic.JsonCodec
import java.time.OffsetDateTime
import java.util.UUID

@JsonCodec
case class EnrichedPerson(
  id: UUID,
  name: String,
  normalizedName: Slug,
  createdAt: OffsetDateTime,
  lastUpdatedAt: OffsetDateTime,
  metadata: Option[Json],
  tmdbId: Option[String],
  popularity: Option[Double])
