package com.teletracker.common.elasticsearch.model

import io.circe.Codec
import io.circe.generic.JsonCodec
import java.time.{LocalDate, OffsetDateTime}

@JsonCodec
case class EsAvailability(
  network_id: Int,
  network_name: Option[String],
  region: String,
  start_date: Option[LocalDate],
  end_date: Option[LocalDate],
  offer_type: String,
  cost: Option[Double],
  currency: Option[String],
  presentation_type: Option[String],
  links: Option[EsAvailabilityLinks],
  num_seasons_available: Option[Int],
  last_updated: Option[OffsetDateTime],
  last_updated_by: Option[String])

object EsAvailability {
  import io.circe.generic.semiauto._

  implicit val codec: Codec[EsAvailability] = deriveCodec

  def distinctFields(
    left: EsAvailability
  ): (Int, String, String, Option[String]) = {
    (
      left.network_id,
      left.region,
      left.offer_type,
      left.presentation_type
    )
  }

  def availabilityEquivalent(
    left: EsAvailability,
    right: EsAvailability
  ): Boolean = {
    distinctFields(left) == distinctFields(right)
  }
}

@JsonCodec
case class EsAvailabilityLinks(web: Option[String])
