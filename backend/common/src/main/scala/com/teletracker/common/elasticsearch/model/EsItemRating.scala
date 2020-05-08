package com.teletracker.common.elasticsearch.model

import io.circe.generic.JsonCodec
import java.time.Instant

@JsonCodec
case class EsItemRating(
  provider_id: Int,
  provider_shortname: String,
  vote_average: Double,
  vote_count: Option[Int],
  weighted_average: Option[Double],
  weighted_last_generated: Option[Instant])
