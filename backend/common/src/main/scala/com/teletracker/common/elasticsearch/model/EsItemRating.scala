package com.teletracker.common.elasticsearch.model

import com.teletracker.common.db.model.ExternalSource
import io.circe.generic.JsonCodec
import java.time.Instant

@JsonCodec
case class EsItemRating(
  provider_id: Int,
  provider_shortname: String,
  vote_average: Double,
  vote_count: Option[Int],
  weighted_average: Option[Double],
  weighted_last_generated: Option[Instant]) {

  lazy val externalSource = ExternalSource.fromString(provider_shortname)
}

case class Rating(
  average: Double,
  count: Option[Int])

object EsItemRating {
  def apply(
    source: ExternalSource,
    voteAverage: Double,
    voteCount: Option[Int],
    weightedAverage: Option[Double],
    weightedLastGenerated: Option[Instant]
  ): EsItemRating =
    EsItemRating(
      provider_id = source.ordinal(),
      provider_shortname = source.getName,
      vote_average = voteAverage,
      vote_count = voteCount,
      weighted_average = weightedAverage,
      weighted_last_generated = weightedLastGenerated
    )
}
