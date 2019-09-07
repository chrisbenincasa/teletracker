package com.teletracker.service.api.model

import com.teletracker.common.db.model.{PersonAssociationType, ThingType}
import com.teletracker.common.util.Slug
import com.teletracker.common.util.json.circe._
import io.circe.Json
import io.circe.generic.JsonCodec
import java.time.{LocalDate, OffsetDateTime}
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
  popularity: Option[Double],
  credits: Option[List[PersonCredit]]) {

  def withCredits(credits: List[PersonCredit]): EnrichedPerson = {
    this.copy(credits = Some(credits))
  }
}

@JsonCodec
case class PersonCredit(
  id: UUID,
  name: String,
  normalizedName: Slug,
  tmdbId: Option[String],
  popularity: Option[Double],
  `type`: ThingType,
  associationType: PersonAssociationType,
  characterName: Option[String],
  releaseDate: Option[LocalDate],
  posterPath: Option[String],
  genreIds: Set[Int])
